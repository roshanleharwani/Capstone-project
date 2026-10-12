export const fadeUp = (reduced: boolean, delay = 0) =>
  reduced
    ? { initial: false, animate: { opacity: 1 }, transition: { duration: 0 } }
    : {
        initial: { opacity: 0, y: 12 },
        animate: { opacity: 1, y: 0 },
        transition: { duration: 0.35, delay, ease: [0.16, 1, 0.3, 1] as const },
      };

export const staggerDelay = (index: number, reduced: boolean) =>
  reduced ? 0 : Math.min(index * 0.06, 0.36);
