import { AppController } from "./app.controller";

describe("AppController", () => {
  it("returns ok health envelope", () => {
    const result = new AppController().health();
    expect(result.success).toBe(true);
    expect(result.data).toEqual({ status: "ok" });
  });
});
