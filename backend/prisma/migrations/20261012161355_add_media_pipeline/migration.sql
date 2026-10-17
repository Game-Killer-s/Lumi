-- CreateEnum
CREATE TYPE "MediaAssetKind" AS ENUM ('AUDIO', 'COVER');

-- CreateEnum
CREATE TYPE "MediaScanStatus" AS ENUM ('PENDING', 'CLEAN', 'INFECTED');

-- CreateEnum
CREATE TYPE "ProcessingStatus" AS ENUM ('QUEUED', 'PROCESSING', 'READY', 'FAILED');

-- CreateTable
CREATE TABLE "MediaAsset" (
    "id" TEXT NOT NULL,
    "trackId" TEXT,
    "kind" "MediaAssetKind" NOT NULL,
    "title" TEXT,
    "storageKey" TEXT NOT NULL,
    "originalName" TEXT,
    "mimeType" TEXT NOT NULL,
    "extension" TEXT NOT NULL,
    "sizeBytes" INTEGER NOT NULL,
    "checksum" TEXT NOT NULL,
    "scanStatus" "MediaScanStatus" NOT NULL DEFAULT 'PENDING',
    "scanReason" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MediaAsset_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "MediaProcessingJob" (
    "id" TEXT NOT NULL,
    "assetId" TEXT NOT NULL,
    "status" "ProcessingStatus" NOT NULL DEFAULT 'QUEUED',
    "targetLufs" DOUBLE PRECISION NOT NULL DEFAULT -14,
    "measuredLufs" DOUBLE PRECISION,
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "failureReason" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "MediaProcessingJob_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "MediaVariant" (
    "id" TEXT NOT NULL,
    "jobId" TEXT NOT NULL,
    "codec" TEXT NOT NULL,
    "bitrateKbps" INTEGER NOT NULL,
    "storageKey" TEXT NOT NULL,
    "sizeBytes" INTEGER,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "MediaVariant_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "MediaAsset_storageKey_key" ON "MediaAsset"("storageKey");

-- CreateIndex
CREATE INDEX "MediaAsset_trackId_idx" ON "MediaAsset"("trackId");

-- CreateIndex
CREATE INDEX "MediaAsset_kind_idx" ON "MediaAsset"("kind");

-- CreateIndex
CREATE UNIQUE INDEX "MediaProcessingJob_assetId_key" ON "MediaProcessingJob"("assetId");

-- CreateIndex
CREATE INDEX "MediaProcessingJob_status_idx" ON "MediaProcessingJob"("status");

-- CreateIndex
CREATE INDEX "MediaVariant_jobId_idx" ON "MediaVariant"("jobId");

-- CreateIndex
CREATE UNIQUE INDEX "MediaVariant_jobId_bitrateKbps_key" ON "MediaVariant"("jobId", "bitrateKbps");

-- AddForeignKey
ALTER TABLE "MediaAsset" ADD CONSTRAINT "MediaAsset_trackId_fkey" FOREIGN KEY ("trackId") REFERENCES "Track"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "MediaProcessingJob" ADD CONSTRAINT "MediaProcessingJob_assetId_fkey" FOREIGN KEY ("assetId") REFERENCES "MediaAsset"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "MediaVariant" ADD CONSTRAINT "MediaVariant_jobId_fkey" FOREIGN KEY ("jobId") REFERENCES "MediaProcessingJob"("id") ON DELETE CASCADE ON UPDATE CASCADE;
