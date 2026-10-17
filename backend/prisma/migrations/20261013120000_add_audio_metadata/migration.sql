-- CreateEnum
CREATE TYPE "MediaMetadataStatus" AS ENUM ('PENDING', 'EXTRACTED', 'FAILED');

-- AlterTable
ALTER TABLE "MediaAsset"
    ADD COLUMN "durationSeconds" DOUBLE PRECISION,
    ADD COLUMN "codec" TEXT,
    ADD COLUMN "sampleRateHz" INTEGER,
    ADD COLUMN "channels" INTEGER,
    ADD COLUMN "bitrateKbps" INTEGER,
    ADD COLUMN "tags" JSONB,
    ADD COLUMN "metadataStatus" "MediaMetadataStatus" NOT NULL DEFAULT 'PENDING',
    ADD COLUMN "metadataError" TEXT;
