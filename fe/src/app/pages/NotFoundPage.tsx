import { IconHome, IconSearch, IconMoodSad } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";

export function NotFoundPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();

  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center px-4 py-16 text-center">
      <div
        className="w-24 h-24 rounded-full flex items-center justify-center mb-6"
        style={{ background: "rgba(238,77,45,0.08)" }}
      >
        <IconMoodSad size={48} style={{ color: "#EE4D2D" }} />
      </div>

      <h1
        className="text-6xl font-black text-foreground mb-2"
        style={{ fontFamily: "'Be Vietnam Pro', sans-serif" }}
      >
        404
      </h1>

      <p className="text-lg font-semibold text-foreground mb-2">
        {t("notFound.title", "Page not found")}
      </p>

      <p className="text-sm text-muted-foreground max-w-md mb-8">
        {t("notFound.description", "The page you're looking for doesn't exist or has been moved. Let's get you back on track.")}
      </p>

      <div className="flex gap-3">
        <button
          onClick={() => navigate("/")}
          className="flex items-center gap-2 px-6 py-3 rounded-xl text-white font-semibold text-sm shadow-lg hover:opacity-90 transition-opacity"
          style={{ background: "linear-gradient(135deg, #EE4D2D, #FF6633)" }}
        >
          <IconHome size={16} />
          {t("notFound.goHome", "Go Home")}
        </button>

        <button
          onClick={() => navigate("/search")}
          className="flex items-center gap-2 px-6 py-3 rounded-xl font-semibold text-sm border-2 transition-colors hover:bg-[rgba(238,77,45,0.06)]"
          style={{ borderColor: "#EE4D2D", color: "#EE4D2D" }}
        >
          <IconSearch size={16} />
          {t("notFound.search", "Search Products")}
        </button>
      </div>
    </div>
  );
}
