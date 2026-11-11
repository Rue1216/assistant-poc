import { createRouter, createWebHistory, type RouterHistory } from "vue-router";
import { setUnauthorizedHandler } from "@/utils/api-client";
import { useAuthStore } from "@/stores/use-auth-store";
import { useToast } from "@/composables/use-toast";

declare module "vue-router" {
  interface RouteMeta {
    permission?: string;
    /** the layout leaves out the left menu (customer 360, as in the prototype) */
    hideSidebar?: boolean;
  }
}

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: "/login", component: () => import("@/views/dev/dev-login-page.vue") },
      {
        path: "/",
        component: () => import("@/components/layout/main-layout.vue"),
        children: [
          { path: "", component: () => import("@/views/home-page.vue") },
          {
            path: "customer",
            component: () => import("@/views/management/customer/customer-list-page.vue"),
          },
          {
            path: "customer/:certNo",
            component: () => import("@/views/management/customer/customer-360-page.vue"),
            props: true,
            meta: { hideSidebar: true },
          },
          {
            path: "maintenance/prompt-management",
            component: () => import("@/views/maintenance/prompt-management/prompt-management-page.vue"),
            meta: { permission: "AI_CONFIG_READ" },
          },
        ],
      },
    ],
  });

  router.beforeEach(async (to) => {
    const auth = useAuthStore();
    if (!auth.user) await auth.fetchMe();

    if (to.path === "/login") return auth.user && !auth.loggingOut ? "/" : true;
    if (!auth.user) return "/login";

    const required = to.meta.permission;
    if (required && !auth.hasPermission(required)) {
      useToast().show("您沒有使用此功能的權限");
      return "/";
    }
    return true;
  });

  setUnauthorizedHandler(() => {
    useAuthStore().user = null;
    if (router.currentRoute.value.path !== "/login") void router.push("/login");
  });

  return router;
}
