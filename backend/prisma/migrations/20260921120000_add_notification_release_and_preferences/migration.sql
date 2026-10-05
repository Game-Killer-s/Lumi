-- AlterTable
ALTER TABLE "NotificationSettings" ADD COLUMN "systemNotifications" BOOLEAN NOT NULL DEFAULT true;

-- AlterTable
ALTER TABLE "UserNotification" ADD COLUMN "payload" JSONB,
ADD COLUMN "readAt" TIMESTAMP(3),
ADD COLUMN "releaseId" TEXT;

-- DropIndex
DROP INDEX "UserNotification_userId_idx";

-- CreateIndex
CREATE INDEX "UserNotification_userId_isRead_idx" ON "UserNotification"("userId", "isRead");

-- CreateIndex
CREATE INDEX "UserNotification_userId_createdAt_idx" ON "UserNotification"("userId", "createdAt");

-- CreateIndex
CREATE UNIQUE INDEX "UserNotification_userId_releaseId_key" ON "UserNotification"("userId", "releaseId");
