import * as XLSX from 'xlsx';
import html2canvas from 'html2canvas-pro';
import { Invoice, ShopInfo, InvoiceSettings } from '../types';
import { formatToman, toPersianDigits, paymentMethodLabel } from './jalali';

// جلوگیری از به‌هم‌ریختن فایل Word وقتی نام‌ها علامت‌هایی مثل & یا < دارند
function esc(v: unknown): string {
  return String(v ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));
}

// دانلود یک فایل/بلاب
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

// Helper to sanitize filename
export function getInvoiceFileName(invoice: Invoice, extension: string): string {
  // Format: فاکتور_{شماره}_{تاریخ}.ext
  const safeNumber = invoice.invoiceNumber.replace(/[\/\\:*?"<>|]/g, '-');
  const safeDate = invoice.date.replace(/[\/\\:*?"<>|]/g, '-');
  return `فاکتور_${safeNumber}_${safeDate}.${extension}`;
}

// 1. Export as Word (.docx / .doc formatted HTML)
export function buildInvoiceWordFile(invoice: Invoice, shopInfo: ShopInfo): File {
  // فایل به‌صورت HTML با پسوند .doc ساخته می‌شود تا Word بدون خطای «فایل خراب است» آن را باز کند
  const fileName = getInvoiceFileName(invoice, 'doc');
  const typeTitles: Record<string, string> = {
    sales: 'فاکتور فروش کالا',
    purchase: 'فاکتور خرید کالا',
    retail: 'فاکتور فروش نقدی (خرده‌فروشی)',
    purchase_return: 'فاکتور برگشت از خرید',
    sales_return: 'فاکتور برگشت از فروش',
  };

  const htmlContent = `
    <!DOCTYPE html>
    <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
    <head>
      <meta charset="utf-8">
      <title>${esc(invoice.invoiceNumber)}</title>
      <style>
        body {
          font-family: 'Vazirmatn', Tahoma, Arial, sans-serif;
          direction: rtl;
          text-align: right;
          margin: 20px;
          color: #1e293b;
        }
        .header {
          border-bottom: 2px solid #0f172a;
          padding-bottom: 15px;
          margin-bottom: 20px;
        }
        .title {
          font-size: 20px;
          font-weight: bold;
          color: #0f172a;
        }
        .subtitle {
          font-size: 12px;
          color: #64748b;
        }
        .meta-box {
          background-color: #f8fafc;
          border: 1px solid #e2e8f0;
          border-radius: 8px;
          padding: 12px;
          margin-bottom: 20px;
          font-size: 13px;
        }
        table {
          width: 100%;
          border-collapse: collapse;
          margin-bottom: 20px;
          font-size: 12px;
        }
        th {
          background-color: #f1f5f9;
          border: 1px solid #cbd5e1;
          padding: 8px;
          font-weight: bold;
          text-align: center;
        }
        td {
          border: 1px solid #cbd5e1;
          padding: 8px;
          text-align: center;
        }
        .td-name {
          text-align: right;
          font-weight: 600;
        }
        .summary-box {
          width: 320px;
          margin-right: auto;
          background-color: #f8fafc;
          border: 1px solid #e2e8f0;
          padding: 12px;
          font-size: 13px;
          border-radius: 8px;
        }
        .summary-row {
          display: flex;
          justify-content: space-between;
          padding: 4px 0;
        }
        .total-row {
          border-top: 2px solid #0f172a;
          margin-top: 6px;
          padding-top: 6px;
          font-size: 15px;
          font-weight: bold;
          color: #b45309;
        }
        .signatures {
          margin-top: 50px;
          display: flex;
          justify-content: space-between;
          font-size: 12px;
          color: #475569;
        }
      </style>
    </head>
    <body>
      <div class="header">
        <div class="title">${esc(shopInfo.name)}</div>
        <div class="subtitle">${esc(shopInfo.address)} · تلفن: ${esc(shopInfo.phone)} · همراه: ${esc(shopInfo.mobile)}</div>
        <div style="margin-top: 10px; font-size: 14px; font-weight: bold; color: #b45309;">
          ${typeTitles[invoice.type] || 'فاکتور'} (شماره: ${esc(invoice.invoiceNumber)})
        </div>
        <div style="font-size: 12px; color: #64748b;">تاریخ: ${toPersianDigits(invoice.date)} - ساعت: ${toPersianDigits(invoice.time)}</div>
      </div>

      <div class="meta-box">
        <strong>طرف حساب:</strong> ${esc(invoice.customerName || invoice.supplierName || 'مشتری محترم نقدی')}<br>
        <strong>تلفن:</strong> ${esc(invoice.customerPhone || invoice.supplierPhone || '-')}<br>
        <strong>ثبت کننده:</strong> ${esc(invoice.createdByName)}
      </div>

      <table>
        <thead>
          <tr>
            <th style="width: 40px;">ردیف</th>
            <th>شرح کالا یا ابزار</th>
            <th>کد کالا</th>
            <th>تعداد</th>
            <th>واحد</th>
            <th>قیمت واحد (تومان)</th>
            <th>مبلغ کل (تومان)</th>
          </tr>
        </thead>
        <tbody>
          ${invoice.items
            .map(
              (item, i) => `
            <tr>
              <td>${toPersianDigits(i + 1)}</td>
              <td class="td-name">${esc(item.productName)}</td>
              <td>${esc(item.productCode)}</td>
              <td>${toPersianDigits(item.quantity)}</td>
              <td>${esc(item.unit)}</td>
              <td>${formatToman(item.unitPrice).replace(' تومان', '')}</td>
              <td><strong>${formatToman(item.totalPrice).replace(' تومان', '')}</strong></td>
            </tr>
          `
            )
            .join('')}
        </tbody>
      </table>

      <div class="summary-box">
        <div>جمع اقلام: <strong>${formatToman(invoice.subtotal)}</strong></div>
        ${invoice.totalDiscount > 0 ? `<div style="color: #e11d48;">تخفیف: -${formatToman(invoice.totalDiscount)}</div>` : ''}
        ${invoice.tax > 0 ? `<div>مالیات: +${formatToman(invoice.tax)}</div>` : ''}
        <div class="total-row">مبلغ نهایی: ${formatToman(invoice.finalTotal)}</div>
        ${invoice.remainingAmount > 0 ? `<div style="color: #dc2626; margin-top: 4px;">مانده حساب: ${formatToman(invoice.remainingAmount)}</div>` : ''}
      </div>

      <table style="width: 100%; margin-top: 50px; border: none; font-size: 12px; color: #475569;">
        <tr>
          <td style="border: none; text-align: center;">مهر و امضای فروشنده (${esc(shopInfo.name)})</td>
          <td style="border: none; text-align: center;">امضاء و تأیید خریدار</td>
        </tr>
      </table>
    </body>
    </html>
  `;

  return new File(['\ufeff' + htmlContent], fileName, { type: 'application/msword' });
}

export function exportInvoiceToWord(invoice: Invoice, shopInfo: ShopInfo): void {
  const file = buildInvoiceWordFile(invoice, shopInfo);
  downloadBlob(file, file.name);
}

// 2. Export as Excel (.xlsx) using SheetJS
export function buildInvoiceExcelFile(invoice: Invoice, shopInfo: ShopInfo): File {
  const fileName = getInvoiceFileName(invoice, 'xlsx');

  // Sheet 1: Items
  const itemsData = invoice.items.map((item, index) => ({
    'ردیف': index + 1,
    'نام کالا': item.productName,
    'کد کالا': item.productCode,
    'واحد': item.unit,
    'تعداد': item.quantity,
    'قیمت واحد (تومان)': item.unitPrice,
    'تخفیف (تومان)': item.discountType === 'amount' ? item.discountValue : (item.discountValue * item.unitPrice) / 100 || 0,
    'مجموع (تومان)': item.totalPrice,
  }));

  // Sheet 2: Summary
  const summaryData = [
    { 'عنوان': 'نام فروشگاه', 'مقدار': shopInfo.name },
    { 'عنوان': 'شماره فاکتور', 'مقدار': invoice.invoiceNumber },
    { 'عنوان': 'نوع فاکتور', 'مقدار': ({ sales: 'فروش', purchase: 'خرید', retail: 'خرده‌فروشی', purchase_return: 'برگشت از خرید', sales_return: 'برگشت از فروش' } as Record<string, string>)[invoice.type] || invoice.type },
    { 'عنوان': 'تاریخ', 'مقدار': invoice.date },
    { 'عنوان': 'ساعت', 'مقدار': invoice.time },
    { 'عنوان': 'طرف حساب', 'مقدار': invoice.customerName || invoice.supplierName || 'مشتری نقدی' },
    { 'عنوان': 'شماره تماس', 'مقدار': invoice.customerPhone || invoice.supplierPhone || '-' },
    { 'عنوان': 'جمع کل اقلام (تومان)', 'مقدار': invoice.subtotal },
    { 'عنوان': 'تخفیف (تومان)', 'مقدار': invoice.totalDiscount },
    { 'عنوان': 'مالیات (تومان)', 'مقدار': invoice.tax },
    { 'عنوان': 'مبلغ نهایی (تومان)', 'مقدار': invoice.finalTotal },
    { 'عنوان': 'مبلغ پرداخت شده (تومان)', 'مقدار': invoice.paidAmount },
    { 'عنوان': 'مانده حساب (تومان)', 'مقدار': invoice.remainingAmount },
    { 'عنوان': 'روش پرداخت', 'مقدار': paymentMethodLabel(invoice.paymentMethod) },
    { 'عنوان': 'ثبت کننده', 'مقدار': invoice.createdByName },
  ];

  const wb = XLSX.utils.book_new();

  const wsItems = XLSX.utils.json_to_sheet(itemsData);
  wsItems['!cols'] = [{ wch: 6 }, { wch: 34 }, { wch: 14 }, { wch: 8 }, { wch: 8 }, { wch: 18 }, { wch: 16 }, { wch: 18 }];
  XLSX.utils.book_append_sheet(wb, wsItems, 'اقلام فاکتور');

  const wsSummary = XLSX.utils.json_to_sheet(summaryData);
  wsSummary['!cols'] = [{ wch: 26 }, { wch: 30 }];
  XLSX.utils.book_append_sheet(wb, wsSummary, 'خلاصه فاکتور');

  // نمایش راست‌به‌چپ
  (wb as any).Workbook = { Views: [{ RTL: true }] };

  const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
  return new File([out], fileName, {
    type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  });
}

export function exportInvoiceToExcel(invoice: Invoice, shopInfo: ShopInfo): void {
  const file = buildInvoiceExcelFile(invoice, shopInfo);
  downloadBlob(file, file.name);
}

// 3. Export as Image (PNG/JPG) using html2canvas
export async function exportElementToImage(
  element: HTMLElement,
  invoice: Invoice,
  format: 'png' | 'jpg' = 'png'
): Promise<string> {
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
  });

  const mimeType = format === 'jpg' ? 'image/jpeg' : 'image/png';
  const dataUrl = canvas.toDataURL(mimeType, 0.95);
  const fileName = getInvoiceFileName(invoice, format);

  const a = document.createElement('a');
  a.href = dataUrl;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);

  return dataUrl;
}

// 4. Export as PDF (via clean styled window print or canvas PDF download)
export async function exportInvoiceToPDF(
  element: HTMLElement,
  invoice: Invoice
): Promise<void> {
  // Use image rendering inside a clean print document / PDF stream
  const canvas = await html2canvas(element, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
  });

  const imgData = canvas.toDataURL('image/png');
  const printWindow = window.open('', '_blank');
  if (!printWindow) {
    // Fallback: download as high-res PNG with notification
    const fileName = getInvoiceFileName(invoice, 'png');
    const a = document.createElement('a');
    a.href = imgData;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    return;
  }

  printWindow.document.write(`
    <!DOCTYPE html>
    <html dir="rtl" lang="fa">
    <head>
      <meta charset="utf-8">
      <title>${getInvoiceFileName(invoice, 'pdf')}</title>
      <style>
        @page {
          size: A4;
          margin: 10mm;
        }
        body {
          margin: 0;
          padding: 0;
          display: flex;
          justify-content: center;
          align-items: flex-start;
          background: #fff;
        }
        img {
          width: 100%;
          max-width: 210mm;
          height: auto;
        }
      </style>
    </head>
    <body>
      <img src="${imgData}" onload="window.print(); window.close();" />
    </body>
    </html>
  `);
  printWindow.document.close();
}

// 5. Bulk Export to Excel
export function exportBulkInvoicesToExcel(
  invoices: Invoice[],
  options: {
    includeItems: boolean;
    includeCustomer: boolean;
    includeAmounts: boolean;
    includeProfit: boolean;
  }
): void {
  const wb = XLSX.utils.book_new();

  // Invoices list sheet
  const rows = invoices.map((inv, idx) => {
    const row: Record<string, string | number> = {
      'ردیف': idx + 1,
      'شماره فاکتور': inv.invoiceNumber,
      'نوع': inv.type,
      'تاریخ': inv.date,
      'ساعت': inv.time,
    };

    if (options.includeCustomer) {
      row['طرف حساب'] = inv.customerName || inv.supplierName || 'مشتری نقدی';
      row['تلفن'] = inv.customerPhone || inv.supplierPhone || '-';
    }

    if (options.includeAmounts) {
      row['جمع کل (تومان)'] = inv.subtotal;
      row['تخفیف (تومان)'] = inv.totalDiscount;
      row['مالیات (تومان)'] = inv.tax;
      row['مبلغ نهایی (تومان)'] = inv.finalTotal;
      row['پرداخت شده (تومان)'] = inv.paidAmount;
      row['مانده (تومان)'] = inv.remainingAmount;
      row['وضعیت تسویه'] = inv.remainingAmount > 0 ? 'نسیه / مانده‌دار' : 'تسویه کامل';
    }

    if (options.includeProfit && inv.totalProfit !== undefined) {
      row['سود (تومان)'] = inv.totalProfit;
    }

    row['تعداد اقلام'] = inv.items.length;
    row['ثبت کننده'] = inv.createdByName;

    return row;
  });

  const wsInvoices = XLSX.utils.json_to_sheet(rows);
  wsInvoices['!views'] = [{ rightToLeft: true }];
  XLSX.utils.book_append_sheet(wb, wsInvoices, 'لیست فاکتورها');

  // If include items, append items detail sheet
  if (options.includeItems) {
    const allItemsRows: Record<string, string | number>[] = [];
    invoices.forEach((inv) => {
      inv.items.forEach((item, itemIdx) => {
        allItemsRows.push({
          'شماره فاکتور': inv.invoiceNumber,
          'ردیف قلم': itemIdx + 1,
          'نام کالا': item.productName,
          'کد کالا': item.productCode,
          'واحد': item.unit,
          'تعداد': item.quantity,
          'قیمت واحد (تومان)': item.unitPrice,
          'مبلغ کل قلم (تومان)': item.totalPrice,
          'تاریخ فاکتور': inv.date,
          'طرف حساب': inv.customerName || inv.supplierName || 'مشتری نقدی',
        });
      });
    });

    const wsItems = XLSX.utils.json_to_sheet(allItemsRows);
    wsItems['!views'] = [{ rightToLeft: true }];
    XLSX.utils.book_append_sheet(wb, wsItems, 'اقلام تجمیعی');
  }

  const dateStr = new Date().toISOString().slice(0, 10);
  XLSX.writeFile(wb, `خروجی_گروهی_فاکتورها_${dateStr}.xlsx`);
}
