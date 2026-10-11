export const NAV_GROUPS = [
  {
    label: "Observe",
    items: [
      { name: "Overview", href: "/" },
      { name: "Cache", href: "/cache" },
    ],
  },
  {
    label: "Control",
    items: [
      { name: "PII", href: "/pii" },
      { name: "Quota", href: "/quota" },
    ],
  },
  {
    label: "Operate",
    items: [
      { name: "Console", href: "/test" },
      { name: "Config", href: "/settings" },
    ],
  },
] as const;

export const NAV_ITEMS = NAV_GROUPS.flatMap((group) => [...group.items]);
