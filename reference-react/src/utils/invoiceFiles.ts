import html2canvas from 'html2canvas-pro';
import type { Invoice, ShopInfo } from '../types';
import {
  getInvoiceFileName,
  buildInvoiceWordFile,
  buildInvoiceExcelFile,
  downloadBlob,
} from './invoiceExport';
import { canvasToPdfFile } from './pdfBuilder';

export type ShareFormat = 'pdf' | 'word' | 'excel' | 'image';

async function waitForRender(): Promise<void> {
  try {
    await (document as Document & { fonts?: { ready: Promise<unknown> } }).fonts?.ready;
  } catch {
    // ignore
  }
  await new Promise<void>((r) => requestAnimationFrame(() => r()));
}

async function renderElementToCanvas(el: HTMLElement): Promise<HTMLCanvasElement> {
  await waitForRender();
  return html2canvas(el, {
    scale: 2,
    useCORS: true,
    backgroundColor: '#ffffff',
    logging: false,
    windowWidth: Math.max(el.scrollWidth, 794),
    windowHeight: el.scrollHeight,
  });
}

function canvasToPngFile(canvas: HTMLCanvasElement, fileName: string): Promise<File> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (b) => (b ? resolve(new File([b], fileName, { type: 'image/png' })) : reject(new Error('toBlob failed'))),
      'image/png'
    );
  });
}

// ساخت فایل فاکتور در قالب انتخاب‌شده (برای اشتراک‌گذاری)
export async function buildInvoiceFile(
  format: ShareFormat,
  invoice: Invoice,
  shopInfo: ShopInfo,
  captureEl: HTMLElement | null
): Promise<File> {
  if (format === 'word') return buildInvoiceWordFile(invoice, shopInfo);
  if (format === 'excel') return buildInvoiceExcelFile(invoice, shopInfo);

  if (!captureEl) throw new Error('capture element missing');
  const canvas = await renderElementToCanvas(captureEl);
  if (format === 'image') return canvasToPngFile(canvas, getInvoiceFileName(invoice, 'png'));
  return canvasToPdfFile(canvas, getInvoiceFileName(invoice, 'pdf'));
}

// اگر مرورگر از ارسال فایل پشتیبانی کند، پنجره‌ی اشتراک‌گذاری گوشی (تلگرام، واتساپ و ...) را با خود فایل باز می‌کند؛
// وگرنه فایل دانلود می‌شود.
export async function shareFileOrDownload(
  file: File,
  text: string,
  title: string
): Promise<'shared' | 'cancelled' | 'downloaded'> {
  const nav = navigator as Navigator & { canShare?: (data: ShareData) => boolean };
  if (typeof nav.share === 'function' && typeof nav.canShare === 'function' && nav.canShare({ files: [file] })) {
    try {
      await nav.share({ files: [file], text, title });
      return 'shared';
    } catch (err) {
      if ((err as DOMException)?.name === 'AbortError') return 'cancelled';
      console.error('share failed, falling back to download:', err);
    }
  }
  downloadBlob(file, file.name);
  return 'downloaded';
}
