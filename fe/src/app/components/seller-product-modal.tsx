import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ImageIcon, Loader2, Plus, Trash2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import {
  sellerProductCreate,
  sellerProductImageActivate,
  sellerProductImageUploadUrl,
  sellerProductUpdate,
} from "../lib/api/endpoints/products";
import { ApiError } from "../lib/api/envelope";

import { ImageWithFallback } from "./image-with-fallback";
import { Modal } from "./ui/modal";
import type { Product } from "./vnshop-data";

interface SellerProductModalProps {
  open: boolean;
  onClose: () => void;
  /** When provided, the modal opens in edit mode and calls sellerProductUpdate. */
  product?: Product | null;
}

interface StagedFile {
  /** Stable id used as React key — survives re-renders. */
  id: string;
  file: File;
  previewUrl: string;
}

const MAX_IMAGES = 5;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ACCEPTED_TYPES = /^image\/(jpeg|png|webp)$/;

function parsePriceInput(raw: string): number {
  return Number(raw.replace(/\D/g, "")) || 0;
}

/**
 * Upload a single file via the presigned-URL → S3 PUT → activate flow.
 * Caller must provide a real `productId`.
 */
async function uploadOne(file: File, productId: string): Promise<string> {
  const presigned = await sellerProductImageUploadUrl(productId, {
    contentType: file.type,
    size: file.size,
  });
  const key = presigned.key ?? presigned.uploadUrl.split("?")[0];

  const putRes = await fetch(presigned.uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": file.type },
    body: file,
  });
  if (!putRes.ok) {
    throw new Error(`Upload thất bại (HTTP ${putRes.status})`);
  }

  const activated = await sellerProductImageActivate(productId, { key });
  return activated.url;
}

/**
 * Public wrapper. Renders nothing when closed so the body's state initialisers
 * fire fresh every time the modal opens (taking `product` as the seed). This
 * removes the previous reset-on-open effect.
 */
export function SellerProductModal({ open, onClose, product }: SellerProductModalProps) {
  if (!open) return null;
  return <SellerProductModalBody onClose={onClose} product={product ?? null} />;
}

function SellerProductModalBody({
  onClose,
  product,
}: {
  onClose: () => void;
  product: Product | null;
}) {
  const qc = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const isEdit = !!product;

  // Form state — initialisers seed from `product` once per mount.
  const [name, setName] = useState(() => product?.name ?? "");
  const [description, setDescription] = useState(() => product?.description ?? "");
  const [price, setPrice] = useState(() => (product?.price ? String(product.price) : ""));
  const [originalPrice, setOriginalPrice] = useState(() =>
    product?.originalPrice ? String(product.originalPrice) : "",
  );
  const [stock, setStock] = useState(() => (product?.stock ? String(product.stock) : "1"));
  const [category, setCategory] = useState(() => product?.category ?? "");

  // Image state. `existingImages` are URLs already attached to the product (edit mode).
  // `staged` are local files the user picked but haven't been uploaded yet.
  const [existingImages, setExistingImages] = useState<string[]>(
    () => product?.images ?? (product?.image ? [product.image] : []),
  );
  const [staged, setStaged] = useState<StagedFile[]>([]);
  const [phase, setPhase] = useState<"idle" | "creating" | "uploading" | "finalising">("idle");

  // Revoke object URLs on unmount to avoid leaks. Because the wrapper only
  // mounts the body while open, this fires on every close.
  useEffect(() => {
    return () => {
      staged.forEach((s) => URL.revokeObjectURL(s.previewUrl));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const totalImageCount = existingImages.length + staged.length;
  const isBusy = phase !== "idle";

  const handleClose = () => {
    if (isBusy) return;
    onClose();
  };

  // Modal handles escape + backdrop dismissal; respect dismissDisabled while busy.

  const enqueueFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const slotsLeft = MAX_IMAGES - totalImageCount;
    if (slotsLeft <= 0) {
      toast.info(`Tối đa ${MAX_IMAGES} ảnh / sản phẩm`);
      return;
    }

    const accepted: StagedFile[] = [];
    Array.from(files)
      .slice(0, slotsLeft)
      .forEach((file) => {
        if (!ACCEPTED_TYPES.test(file.type)) {
          toast.error(`${file.name}: Chỉ chấp nhận JPG, PNG, hoặc WebP`);
          return;
        }
        if (file.size > MAX_IMAGE_BYTES) {
          toast.error(`${file.name}: Vượt quá ${MAX_IMAGE_BYTES / (1024 * 1024)}MB`);
          return;
        }
        accepted.push({
          id: `${file.name}-${file.size}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
          file,
          previewUrl: URL.createObjectURL(file),
        });
      });

    if (accepted.length > 0) setStaged((prev) => [...prev, ...accepted]);
  };

  const removeStaged = (id: string) => {
    setStaged((prev) => {
      const target = prev.find((s) => s.id === id);
      if (target) URL.revokeObjectURL(target.previewUrl);
      return prev.filter((s) => s.id !== id);
    });
  };

  const removeExistingImage = (url: string) => {
    setExistingImages((prev) => prev.filter((u) => u !== url));
  };

  /**
   * Save flow:
   *
   *   Edit mode (product exists):
   *     1. Upload all staged files in parallel using product.id.
   *     2. PUT /sellers/me/products/{id} with the merged image list.
   *
   *   Create mode (no product id yet):
   *     1. POST /sellers/me/products with no images (placeholder).
   *     2. Upload all staged files in parallel using the new id.
   *     3. PUT /sellers/me/products/{id} with the image list.
   *
   * Single mutation so the UI stays in sync; per-phase status drives the button label.
   */
  const saveMutation = useMutation({
    mutationFn: async (): Promise<{ id: string; isNew: boolean }> => {
      const priceNum = parsePriceInput(price);
      const originalPriceNum = originalPrice ? parsePriceInput(originalPrice) : undefined;
      const stockNum = parsePriceInput(stock);

      const baseBody = {
        name: name.trim(),
        description: description.trim() || undefined,
        price: priceNum,
        originalPrice: originalPriceNum,
        stock: stockNum,
        category: category.trim() || undefined,
      };

      let productId: string;
      let isNew = false;

      if (isEdit && product) {
        productId = product.id;
      } else {
        setPhase("creating");
        const created = await sellerProductCreate({ ...baseBody, images: [], image: undefined });
        productId = created.id;
        isNew = true;
      }

      let uploadedUrls: string[] = [];
      if (staged.length > 0) {
        setPhase("uploading");
        const settled = await Promise.allSettled(staged.map((s) => uploadOne(s.file, productId)));
        uploadedUrls = settled
          .filter((r): r is PromiseFulfilledResult<string> => r.status === "fulfilled")
          .map((r) => r.value);
        const failed = settled.filter((r) => r.status === "rejected");
        if (failed.length > 0) {
          // Surface first error but don't abort — partial uploads are still useful.
          const first = failed[0];
          const message =
            first.reason instanceof ApiError
              ? first.reason.message
              : first.reason instanceof Error
                ? first.reason.message
                : "Một số ảnh tải lên thất bại";
          toast.error(`${failed.length}/${staged.length} ảnh tải lên thất bại: ${message}`);
        }
      }

      const allImages = [...existingImages, ...uploadedUrls];
      // Skip the final PUT when nothing changed in edit mode (no new uploads, no removals).
      const needsFinalUpdate =
        isNew ||
        uploadedUrls.length > 0 ||
        existingImages.length !== (product?.images?.length ?? (product?.image ? 1 : 0)) ||
        baseFieldsChanged(product, baseBody);

      if (needsFinalUpdate) {
        setPhase("finalising");
        await sellerProductUpdate(productId, {
          ...baseBody,
          images: allImages,
          image: allImages[0],
        });
      }

      return { id: productId, isNew };
    },
    onSuccess: ({ id, isNew }) => {
      void qc.invalidateQueries({ queryKey: ["catalog", "products"] });
      void qc.invalidateQueries({ queryKey: ["catalog", "products", "detail", id] });
      toast.success(isNew ? "Đã đăng sản phẩm" : "Đã cập nhật sản phẩm");
      onClose();
    },
    onError: (err) =>
      toast.error(
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : isEdit
              ? "Không thể cập nhật sản phẩm"
              : "Không thể đăng sản phẩm",
      ),
    onSettled: () => setPhase("idle"),
  });

  const handleSubmit = () => {
    if (!name.trim()) {
      toast.error("Vui lòng nhập tên sản phẩm");
      return;
    }
    const priceNum = parsePriceInput(price);
    if (priceNum <= 0) {
      toast.error("Giá sản phẩm phải lớn hơn 0");
      return;
    }
    if (originalPrice && parsePriceInput(originalPrice) < priceNum) {
      toast.error("Giá gốc phải lớn hơn hoặc bằng giá bán");
      return;
    }
    if (parsePriceInput(stock) < 0) {
      toast.error("Tồn kho không hợp lệ");
      return;
    }
    saveMutation.mutate();
  };

  if (!open) return null;

  const submitLabel = (() => {
    switch (phase) {
      case "creating":
        return "Đang tạo sản phẩm...";
      case "uploading":
        return `Đang tải ${staged.length} ảnh...`;
      case "finalising":
        return "Đang lưu...";
      default:
        return isEdit ? "Lưu thay đổi" : "Đăng sản phẩm";
    }
  })();

  return (
    <Modal
      open
      onClose={handleClose}
      dismissDisabled={isBusy}
      size="lg"
      scrollable
      title={isEdit ? "Sửa sản phẩm" : "Thêm sản phẩm mới"}
      footer={
        <>
          <button
            type="button"
            onClick={handleClose}
            disabled={isBusy}
            className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 disabled:opacity-50"
          >
            Huỷ
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={isBusy}
            className="flex-1 py-2.5 rounded-xl text-white text-sm font-semibold flex items-center justify-center gap-2 disabled:opacity-50"
            style={{ background: "linear-gradient(135deg, #FF6200, #ff8a40)" }}
          >
            {isBusy ? (
              <>
                <Loader2 size={14} className="animate-spin" /> {submitLabel}
              </>
            ) : (
              <>
                <Plus size={14} /> {submitLabel}
              </>
            )}
          </button>
        </>
      }
    >
      <div className="space-y-5">
        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-2">
            Ảnh sản phẩm{" "}
            <span className="text-gray-400 font-normal">
              ({totalImageCount}/{MAX_IMAGES})
            </span>
          </label>
          <div className="grid grid-cols-4 gap-3">
            {existingImages.map((url) => (
              <div
                key={url}
                className="relative aspect-square rounded-xl overflow-hidden bg-gray-100 border border-gray-200"
              >
                <ImageWithFallback src={url} alt="" className="w-full h-full object-cover" />
                <button
                  type="button"
                  onClick={() => removeExistingImage(url)}
                  disabled={isBusy}
                  className="absolute top-1 right-1 w-6 h-6 rounded-full bg-white/90 flex items-center justify-center hover:bg-white disabled:opacity-50"
                  aria-label="Xoá ảnh"
                >
                  <Trash2 size={12} className="text-red-500" />
                </button>
              </div>
            ))}
            {staged.map((s) => (
              <div
                key={s.id}
                className="relative aspect-square rounded-xl overflow-hidden bg-gray-100 border border-gray-200"
              >
                <img src={s.previewUrl} alt={s.file.name} className="w-full h-full object-cover" />
                {phase === "uploading" ? (
                  <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                    <Loader2 size={20} className="text-white animate-spin" />
                  </div>
                ) : null}
                <button
                  type="button"
                  onClick={() => removeStaged(s.id)}
                  disabled={isBusy}
                  className="absolute top-1 right-1 w-6 h-6 rounded-full bg-white/90 flex items-center justify-center hover:bg-white disabled:opacity-50"
                  aria-label="Bỏ ảnh"
                >
                  <Trash2 size={12} className="text-red-500" />
                </button>
              </div>
            ))}
            {totalImageCount < MAX_IMAGES ? (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isBusy}
                className="aspect-square rounded-xl border-2 border-dashed border-gray-200 flex flex-col items-center justify-center gap-1.5 text-gray-400 hover:border-[#00BFB3] hover:text-[#00BFB3] transition-colors disabled:opacity-50"
              >
                <ImageIcon size={20} />
                <span className="text-[11px] font-medium">Thêm ảnh</span>
              </button>
            ) : null}
          </div>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            onChange={(e) => {
              enqueueFiles(e.target.files);
              e.target.value = "";
            }}
          />
          <p className="text-[11px] text-gray-400 mt-2">
            JPG, PNG, hoặc WebP. Tối đa {MAX_IMAGE_BYTES / (1024 * 1024)}MB mỗi ảnh, {MAX_IMAGES}{" "}
            ảnh / sản phẩm. Ảnh sẽ được tải lên khi bạn nhấn Lưu.
          </p>
        </div>

        <div>
          <label
            htmlFor="seller-product-name"
            className="block text-sm font-semibold text-gray-700 mb-1.5"
          >
            Tên sản phẩm
          </label>
          <input
            id="seller-product-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="VD: Tai nghe Sony WH-1000XM5"
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3]"
            disabled={isBusy}
          />
        </div>

        <div>
          <label
            htmlFor="seller-product-description"
            className="block text-sm font-semibold text-gray-700 mb-1.5"
          >
            Mô tả
          </label>
          <textarea
            id="seller-product-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            placeholder="Mô tả chi tiết sản phẩm..."
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none"
            disabled={isBusy}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label
              htmlFor="seller-product-price"
              className="block text-sm font-semibold text-gray-700 mb-1.5"
            >
              Giá bán (VND)
            </label>
            <input
              id="seller-product-price"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              placeholder="990000"
              inputMode="numeric"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3]"
              disabled={isBusy}
            />
          </div>
          <div>
            <label
              htmlFor="seller-product-original-price"
              className="block text-sm font-semibold text-gray-700 mb-1.5"
            >
              Giá gốc (tuỳ chọn)
            </label>
            <input
              id="seller-product-original-price"
              value={originalPrice}
              onChange={(e) => setOriginalPrice(e.target.value)}
              placeholder="1290000"
              inputMode="numeric"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3]"
              disabled={isBusy}
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label
              htmlFor="seller-product-stock"
              className="block text-sm font-semibold text-gray-700 mb-1.5"
            >
              Tồn kho
            </label>
            <input
              id="seller-product-stock"
              value={stock}
              onChange={(e) => setStock(e.target.value)}
              placeholder="100"
              inputMode="numeric"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3]"
              disabled={isBusy}
            />
          </div>
          <div>
            <label
              htmlFor="seller-product-category"
              className="block text-sm font-semibold text-gray-700 mb-1.5"
            >
              Danh mục
            </label>
            <input
              id="seller-product-category"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="electronics"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3]"
              disabled={isBusy}
            />
          </div>
        </div>
      </div>
    </Modal>
  );
}

function baseFieldsChanged(
  product: Product | null | undefined,
  body: {
    name: string;
    description: string | undefined;
    price: number;
    originalPrice: number | undefined;
    stock: number;
    category: string | undefined;
  },
): boolean {
  if (!product) return true;
  return (
    product.name !== body.name ||
    (product.description ?? "") !== (body.description ?? "") ||
    product.price !== body.price ||
    (product.originalPrice ?? undefined) !== body.originalPrice ||
    product.stock !== body.stock ||
    (product.category ?? undefined) !== body.category
  );
}
