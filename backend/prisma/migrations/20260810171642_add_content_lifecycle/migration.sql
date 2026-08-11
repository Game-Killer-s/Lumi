-- AlterEnum
-- This migration adds more than one value to an enum.
-- With PostgreSQL versions 11 and earlier, this is not possible
-- in a single migration. This can be worked around by creating
-- multiple migrations, each migration adding only one value to
-- the enum.


ALTER TYPE "TrackStatus" ADD VALUE 'UPLOADED';
ALTER TYPE "TrackStatus" ADD VALUE 'PROCESSING';

-- AlterTable
ALTER TABLE "Track" ADD COLUMN     "blockReason" TEXT;

-- CreateTable
CREATE TABLE "TrackAuditLog" (
    "id" TEXT NOT NULL,
    "trackId" TEXT NOT NULL,
    "actorId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "reason" TEXT,
    "details" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "TrackAuditLog_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "TrackAuditLog_trackId_idx" ON "TrackAuditLog"("trackId");

-- AddForeignKey
ALTER TABLE "TrackAuditLog" ADD CONSTRAINT "TrackAuditLog_trackId_fkey" FOREIGN KEY ("trackId") REFERENCES "Track"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "TrackAuditLog" ADD CONSTRAINT "TrackAuditLog_actorId_fkey" FOREIGN KEY ("actorId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
