import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Invoice, ShopInfo, User } from '../types';
import { formatToman, toPersianDigits } from '../utils/jalali';
import { buildInvoiceFile, shareFileOrDownload, ShareFormat } from '../utils/invoiceFiles';
import { InvoiceTemplateRenderer, PrintOptions } from './PrintTemplates';
import { StorageService } from '../services/storageService';
import {
  Share2,
  X,
  MessageCircle,
  Send,
  Mail,
  Smartphone,
  MoreHorizontal,
  FileText,
  FileSpreadsheet,
  Image as ImageIcon,
  Check,
} from 'lucide-react';

const CAPTURE_OPTIONS: PrintOptions = {
  includeLogo: true,
  includeAddressPhone: true,
  includeSignatures: true,
  showProfit: false,
  includeBarcode: true,
};

interface Props {
  invoice: Invoice | null;
  shopInfo: ShopInfo;
  currentUser: User;
  isOpen: boolean;
  onClose: () => void;
}

export const InvoiceShareModal: React.FC<Props> = ({
  invoice,
  shopInfo,
  currentUser,
  isOpen,
  onClose,
}) => {
  const [selectedFormat, setSelectedFormat] = useState<ShareFormat>('pdf');
  const [copiedText, setCopiedText] = useState(false);
  const [notice, setNotice] = useState<{ text: string; error: boolean } | null>(null);
  const [isPreparing, setIsPreparing] = useState(false);
  const captureRef = useRef<HTMLDivElement>(null);
  const fileCache = useRef<Partial<Record<ShareFormat, Promise<File>>>>({});
  const invoiceKey = invoice?.id ?? invoice?.invoiceNumber ?? '';

  // با باز شدن پنجره یا عوض شدن فاکتور، فایل‌های ساخته‌شده‌ی قبلی پاک می‌شوند
  useEffect(() => {
    fileCache.current = {};
    setNotice(null);
  }, [isOpen, invoiceKey]);

  const ensureFile = (format: ShareFormat): Promise<File> => {
    if (!invoice) return Promise.reject(new Error('no invoice'));
    const cached = fileCache.current[format];
    if (cached) return cached;
    const p = buildInvoiceFile(format, invoice, shopInfo, captureRef.current);
    fileCache.current[format] = p;
    p.catch(() => {
      delete fileCache.current[format];
    });
    return p;
  };

  // فایل را از قبل آماده می‌کنیم تا هنگام زدن دکمه‌ی ارسال، معطلی نداشته باشد
  useEffect(() => {
    if (!isOpen || !invoice) return;
    let cancelled = false;
    setIsPreparing(true);
    ensureFile(selectedFormat)
      .catch((err) => console.error('prepare file failed:', err))
      .finally(() => {
        if (!cancelled) setIsPreparing(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, invoiceKey, selectedFormat]);

  if (!isOpen || !invoice) return null;

  const invoiceSummaryText = `📄 فاکتور ${invoice.invoiceNumber}
🏪 فروشگاه ابزار حامی
👤 خریدار: ${invoice.customerName || invoice.supplierName || 'مشتری محترم نقدی'}
📅 تاریخ: ${toPersianDigits(invoice.date)} - ساعت: ${toPersianDigits(invoice.time)}
📦 تعداد اقلام: ${toPersianDigits(invoice.items.length)} قلم
💰 مبلغ نهایی: ${formatToman(invoice.finalTotal)}
${invoice.remainingAmount > 0 ? `⚠️ مانده دفتری: ${formatToman(invoice.remainingAmount)}\n` : '✅ تسویه کامل\n'}
با سپاس از خرید شما · ابزار حامی`;

  const copyText = async () => {
    try {
      await navigator.clipboard.writeText(invoiceSummaryText);
      setCopiedText(true);
      setTimeout(() => setCopiedText(false), 2500);
    } catch {
      // ignore
    }
  };

  // مسیر جایگزین (وقتی مرورگر ارسال مستقیم فایل را پشتیبانی نمی‌کند): فایل دانلود شده و متن در برنامه‌ی انتخابی باز می‌شود
  const openChannelWithText = (channel: 'whatsapp' | 'telegram' | 'email' | 'sms' | 'other') => {
    const encodedText = encodeURIComponent(invoiceSummaryText);
    if (channel === 'whatsapp') {
      const phoneClean = (invoice.customerPhone || '').replace(/\D/g, '');
      const waUrl = phoneClean
        ? `https://wa.me/98${phoneClean.startsWith('0') ? phoneClean.slice(1) : phoneClean}?text=${encodedText}`
        : `https://api.whatsapp.com/send?text=${encodedText}`;
      window.open(waUrl, '_blank');
    } else if (channel === 'telegram') {
      window.open(`https://t.me/share/url?url=${encodeURIComponent(shopInfo.website || 'hamitools.ir')}&text=${encodedText}`, '_blank');
    } else if (channel === 'email') {
      const subject = encodeURIComponent(`فاکتور ${invoice.invoiceNumber} - ${shopInfo.name}`);
      window.open(`mailto:?subject=${subject}&body=${encodedText}`, '_blank');
    } else if (channel === 'sms') {
      window.open(`sms:?body=${encodedText}`, '_blank');
    } else {
      copyText();
    }
  };

  const handleShare = async (channel: 'whatsapp' | 'telegram' | 'email' | 'sms' | 'other') => {
    StorageService.logInvoiceShared(invoice, channel, currentUser);
    setNotice(null);

    let file: File;
    try {
      file = await ensureFile(selectedFormat);
    } catch (err) {
      console.error('build file failed:', err);
      setNotice({ text: 'خطا در ساخت فایل فاکتور', error: true });
      return;
    }

    const result = await shareFileOrDownload(file, invoiceSummaryText, `فاکتور ${invoice.invoiceNumber}`);
    if (result === 'shared') {
      setNotice({ text: 'فایل فاکتور ارسال شد.', error: false });
    } else if (result === 'downloaded') {
      setNotice({
        text: 'ارسال مستقیم فایل در این مرورگر ممکن نیست. فایل در پوشه‌ی دانلودها ذخیره شد؛ در برنامه‌ی مقصد آن را به‌عنوان پیوست اضافه کنید.',
        error: false,
      });
      openChannelWithText(channel);
    }
  };

  return (
    <>
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/65 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="w-full max-w-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 rounded-3xl shadow-2xl overflow-hidden border border-slate-200 dark:border-slate-800">
        {/* Top Header */}
        <div className="px-5 py-3.5 bg-slate-900 text-white flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Share2 className="w-4 h-4 text-amber-500" />
            <h3 className="font-bold text-sm">اشتراک‌گذاری فاکتور</h3>
            <span className="text-xs font-mono text-amber-400">({invoice.invoiceNumber})</span>
          </div>

          <button onClick={onClose} className="text-slate-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-5 space-y-5 text-xs">
          {/* Format Selection */}
          <div className="space-y-2">
            <label className="font-bold text-slate-800 dark:text-slate-200 block text-xs">
              فرمت فایل ارسالی:
            </label>
            <div className="grid grid-cols-4 gap-2">
              {[
                { id: 'pdf', label: 'PDF', icon: FileText, color: 'text-rose-500' },
                { id: 'word', label: 'Word', icon: FileText, color: 'text-blue-500' },
                { id: 'excel', label: 'Excel', icon: FileSpreadsheet, color: 'text-emerald-500' },
                { id: 'image', label: 'تصویر', icon: ImageIcon, color: 'text-purple-500' },
              ].map((fmt) => {
                const isSel = selectedFormat === fmt.id;
                const Icon = fmt.icon;
                return (
                  <button
                    key={fmt.id}
                    onClick={() => setSelectedFormat(fmt.id as any)}
                    className={`p-2.5 rounded-2xl border text-center transition-all flex flex-col items-center gap-1.5 ${
                      isSel
                        ? 'bg-amber-600 text-white border-amber-700 shadow-sm'
                        : 'bg-slate-50 dark:bg-slate-800 border-slate-200 dark:border-slate-700 hover:bg-slate-100 text-slate-700 dark:text-slate-300'
                    }`}
                  >
                    <Icon className={`w-5 h-5 ${isSel ? 'text-white' : fmt.color}`} />
                    <span className="font-bold text-[11px]">{fmt.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Share Channels */}
          <div className="space-y-2.5">
            <label className="font-bold text-slate-800 dark:text-slate-200 block text-xs">
              اشتراک‌گذاری با:
            </label>

            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => handleShare('whatsapp')}
                className="p-3 bg-emerald-50 hover:bg-emerald-100 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-200 rounded-2xl border border-emerald-300 dark:border-emerald-800 font-bold flex items-center justify-center gap-2 transition-transform active:scale-95"
              >
                <MessageCircle className="w-5 h-5 text-emerald-600" />
                <span>📱 واتساپ</span>
              </button>

              <button
                onClick={() => handleShare('telegram')}
                className="p-3 bg-blue-50 hover:bg-blue-100 dark:bg-blue-950/40 text-blue-800 dark:text-blue-200 rounded-2xl border border-blue-300 dark:border-blue-800 font-bold flex items-center justify-center gap-2 transition-transform active:scale-95"
              >
                <Send className="w-5 h-5 text-blue-600" />
                <span>✈️ تلگرام</span>
              </button>

              <button
                onClick={() => handleShare('email')}
                className="p-3 bg-amber-50 hover:bg-amber-100 dark:bg-amber-950/40 text-amber-800 dark:text-amber-200 rounded-2xl border border-amber-300 dark:border-amber-800 font-bold flex items-center justify-center gap-2 transition-transform active:scale-95"
              >
                <Mail className="w-5 h-5 text-amber-600" />
                <span>📧 ایمیل</span>
              </button>

              <button
                onClick={() => handleShare('sms')}
                className="p-3 bg-purple-50 hover:bg-purple-100 dark:bg-purple-950/40 text-purple-800 dark:text-purple-200 rounded-2xl border border-purple-300 dark:border-purple-800 font-bold flex items-center justify-center gap-2 transition-transform active:scale-95"
              >
                <Smartphone className="w-5 h-5 text-purple-600" />
                <span>📞 پیامک</span>
              </button>
            </div>

            <button
              onClick={() => handleShare('other')}
              className="w-full p-2.5 bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 text-slate-800 dark:text-slate-200 rounded-xl border border-slate-300 dark:border-slate-700 font-semibold flex items-center justify-center gap-2 transition-colors"
            >
              <MoreHorizontal className="w-4 h-4" />
              <span>💬 سایر روش‌ها / کپی متن فاکتور</span>
            </button>

            {copiedText && (
              <p className="text-[11px] text-emerald-600 dark:text-emerald-400 font-bold text-center flex items-center justify-center gap-1">
                <Check className="w-3.5 h-3.5" />
                <span>متن فاکتور در کلیپ‌بورد کپی شد!</span>
              </p>
            )}

            {isPreparing && !notice && (
              <p className="text-[11px] text-slate-500 dark:text-slate-400 text-center">در حال آماده‌سازی فایل...</p>
            )}

            {notice && (
              <p
                className={`text-[11px] font-bold text-center flex items-center justify-center gap-1 ${
                  notice.error ? 'text-red-600 dark:text-red-400' : 'text-emerald-600 dark:text-emerald-400'
                }`}
              >
                {notice.error ? <X className="w-3.5 h-3.5 shrink-0" /> : <Check className="w-3.5 h-3.5 shrink-0" />}
                <span>{notice.text}</span>
              </p>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3 bg-slate-50 dark:bg-slate-850 border-t border-slate-200 dark:border-slate-800 text-left">
          <button
            onClick={onClose}
            className="px-4 py-1.5 bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 text-slate-800 dark:text-slate-200 rounded-xl text-xs font-semibold"
          >
            بستن
          </button>
        </div>
      </div>
    </div>

    {/* نسخه‌ی مخفی فاکتور با عرض A4 برای ساخت عکس و PDF */}
    {createPortal(
      <div
        ref={captureRef}
        aria-hidden="true"
        style={{ position: 'absolute', left: '-10000px', top: 0, width: '794px', background: '#ffffff', padding: '24px' }}
      >
        <InvoiceTemplateRenderer
          templateId="official"
          invoice={invoice}
          shopInfo={shopInfo}
          options={CAPTURE_OPTIONS}
        />
      </div>,
      document.body
    )}
    </>
  );
};
