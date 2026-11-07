-- Скарги з плеєра: на трек або на коментар під треком (ТЗ 6.3.12).
-- targetId зберігає id цілі (трек або коментар), comment — пояснення слухача.
ALTER TABLE "Report" ADD COLUMN "target" TEXT NOT NULL DEFAULT 'track';
ALTER TABLE "Report" ADD COLUMN "targetId" TEXT;
ALTER TABLE "Report" ADD COLUMN "comment" TEXT;

-- Для вже створених скарг ціль — сам трек.
UPDATE "Report" SET "targetId" = "trackId" WHERE "targetId" IS NULL;

CREATE INDEX "Report_status_idx" ON "Report"("status");
CREATE INDEX "Report_trackId_idx" ON "Report"("trackId");
