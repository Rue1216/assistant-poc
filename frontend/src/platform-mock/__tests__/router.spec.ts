import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory } from "vue-router";
import {
  AxiosError,
  type AxiosAdapter,
  type InternalAxiosRequestConfig,
} from "axios";

const show = vi.fn();
vi.mock("../useToast", () => ({ useToast: () => ({ show }) }));

import { apiClient } from "../apiClient";
import { createAppRouter } from "../router";
import { useAuthStore } from "../useAuthStore";

function meAdapter(me: object | null): AxiosAdapter {
  return (config: InternalAxiosRequestConfig) => {
    if (me) {
      return Promise.resolve({
        status: 200,
        statusText: "OK",
        data: me,
        headers: {},
        config,
      });
    }
    return Promise.reject(
      new AxiosError("401", "ERR_BAD_REQUEST", config, null, {
        status: 401,
        statusText: "",
        data: { code: "AI_UNAUTHENTICATED", message: "請先登入" },
        headers: {},
        config,
      }),
    );
  };
}

const fcOfficer = {
  username: "ICRM00028",
  displayName: "理專",
  roleKey: "RM",
  roleName: "理專",
  permissions: ["CUSTOMER_360_VIEW", "AI_ASSISTANT_USE"],
};
const itAdmin = {
  ...fcOfficer,
  username: "EEE22222",
  permissions: [
    ...fcOfficer.permissions,
    "AI_CONFIG_READ",
    "AI_CONFIG_EDIT",
    "AI_CONFIG_PUBLISH",
  ],
};

describe("router", () => {
  beforeEach(() => {
    show.mockReset();
    setActivePinia(createPinia());
  });

  it("redirects to login when not authenticated", async () => {
    apiClient.defaults.adapter = meAdapter(null);
    const router = createAppRouter(createMemoryHistory());
    await router.push("/customer");
    expect(router.currentRoute.value.path).toBe("/login");
    expect(show).not.toHaveBeenCalled(); // the 401 from /me is silent
  });

  it("blocks prompt management without AI_CONFIG_READ", async () => {
    apiClient.defaults.adapter = meAdapter(fcOfficer);
    const router = createAppRouter(createMemoryHistory());
    await router.push("/maintenance/prompt-management");
    expect(router.currentRoute.value.path).toBe("/");
    expect(show).toHaveBeenCalledWith("您沒有使用此功能的權限");
  });

  async function trigger401() {
    const saved = apiClient.defaults.adapter;
    apiClient.defaults.adapter = meAdapter(null);
    await apiClient.get("/mock/customers").catch(() => undefined);
    apiClient.defaults.adapter = saved;
  }

  it("unauthorized handler pushes /login once when elsewhere", async () => {
    apiClient.defaults.adapter = meAdapter(fcOfficer);
    const router = createAppRouter(createMemoryHistory());
    await router.push("/customer");
    const push = vi.spyOn(router, "push");
    await trigger401();
    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith("/login");
    expect(useAuthStore().user).toBeNull();
  });

  it("unauthorized handler does not push again when already on /login", async () => {
    apiClient.defaults.adapter = meAdapter(null);
    const router = createAppRouter(createMemoryHistory());
    await router.push("/login");
    expect(router.currentRoute.value.path).toBe("/login");
    const push = vi.spyOn(router, "push");
    await trigger401();
    expect(push).not.toHaveBeenCalled();
  });

  it("allows prompt management with AI_CONFIG_READ", async () => {
    apiClient.defaults.adapter = meAdapter(itAdmin);
    const router = createAppRouter(createMemoryHistory());
    await router.push("/maintenance/prompt-management");
    expect(router.currentRoute.value.path).toBe(
      "/maintenance/prompt-management",
    );
    expect(useAuthStore().hasPermission("AI_CONFIG_READ")).toBe(true);
  });
});
