// Приватність за замовчуванням для нових користувачів.
// Значення дублюють @default у Prisma, щоб політика була явною і в коді.
export const DEFAULT_PROFILE_PRIVACY = {
  isPublic: true,
  showCurrentListening: true,
} as const;
