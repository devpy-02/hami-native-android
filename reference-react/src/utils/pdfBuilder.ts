// ساخت فایل PDF بدون کتابخانه‌ی خارجی: هر صفحه یک عکس JPEG است (مناسب فاکتور)

const PAGE_W = 595.28; // A4 به واحد point
const PAGE_H = 841.89;
const MARGIN = 24;

export interface PdfPageImage {
  jpeg: Uint8Array;
  width: number; // پیکسل
  height: number; // پیکسل
}

export function buildPdfFromJpegs(pages: PdfPageImage[]): Blob {
  const enc = new TextEncoder();
  const chunks: Uint8Array[] = [];
  const offsets: number[] = [];
  let length = 0;

  const push = (d: Uint8Array | string) => {
    const u = typeof d === 'string' ? enc.encode(d) : d;
    chunks.push(u);
    length += u.length;
  };
  const startObj = (n: number) => {
    offsets[n] = length;
    push(`${n} 0 obj\n`);
  };

  const n = pages.length;
  const totalObjects = 2 + n * 3;
  const fmt = (v: number) => v.toFixed(2);

  push('%PDF-1.4\n');

  startObj(1);
  push('<< /Type /Catalog /Pages 2 0 R >>\nendobj\n');

  startObj(2);
  const kids = pages.map((_, i) => `${3 + i * 3} 0 R`).join(' ');
  push(`<< /Type /Pages /Kids [${kids}] /Count ${n} >>\nendobj\n`);

  pages.forEach((p, i) => {
    const pageObj = 3 + i * 3;
    const contentObj = pageObj + 1;
    const imgObj = pageObj + 2;

    const drawW = PAGE_W - 2 * MARGIN;
    const drawH = (p.height * drawW) / p.width;
    const x = MARGIN;
    const y = PAGE_H - MARGIN - drawH;

    startObj(pageObj);
    push(
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(PAGE_W)} ${fmt(PAGE_H)}] ` +
        `/Resources << /XObject << /Im0 ${imgObj} 0 R >> >> /Contents ${contentObj} 0 R >>\nendobj\n`
    );

    const content = `q ${fmt(drawW)} 0 0 ${fmt(drawH)} ${fmt(x)} ${fmt(y)} cm /Im0 Do Q`;
    startObj(contentObj);
    push(`<< /Length ${content.length} >>\nstream\n${content}\nendstream\nendobj\n`);

    startObj(imgObj);
    push(
      `<< /Type /XObject /Subtype /Image /Width ${p.width} /Height ${p.height} ` +
        `/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${p.jpeg.length} >>\nstream\n`
    );
    push(p.jpeg);
    push('\nendstream\nendobj\n');
  });

  const xrefOffset = length;
  push(`xref\n0 ${totalObjects + 1}\n`);
  push('0000000000 65535 f \n');
  for (let i = 1; i <= totalObjects; i++) {
    push(`${String(offsets[i]).padStart(10, '0')} 00000 n \n`);
  }
  push(`trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`);

  return new Blob(chunks as BlobPart[], { type: 'application/pdf' });
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('canvas.toBlob failed'))), type, quality);
  });
}

// بوم (canvas) را به صفحه‌های A4 تقسیم می‌کند و PDF می‌سازد
export async function canvasToPdfFile(canvas: HTMLCanvasElement, fileName: string): Promise<File> {
  const drawW = PAGE_W - 2 * MARGIN;
  const pageHpx = Math.floor(((PAGE_H - 2 * MARGIN) * canvas.width) / drawW);
  const pages: PdfPageImage[] = [];

  for (let y = 0; y < canvas.height; y += pageHpx) {
    const h = Math.min(pageHpx, canvas.height - y);
    const slice = document.createElement('canvas');
    slice.width = canvas.width;
    slice.height = h;
    const ctx = slice.getContext('2d');
    if (!ctx) throw new Error('no 2d context');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, slice.width, h);
    ctx.drawImage(canvas, 0, y, canvas.width, h, 0, 0, canvas.width, h);
    const blob = await canvasToBlob(slice, 'image/jpeg', 0.92);
    pages.push({ jpeg: new Uint8Array(await blob.arrayBuffer()), width: slice.width, height: h });
  }

  return new File([buildPdfFromJpegs(pages)], fileName, { type: 'application/pdf' });
}
