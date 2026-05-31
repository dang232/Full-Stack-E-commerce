import { toast } from "sonner";
export const comingSoon = (feature: string) => toast.info(`${feature} is coming soon!`);
