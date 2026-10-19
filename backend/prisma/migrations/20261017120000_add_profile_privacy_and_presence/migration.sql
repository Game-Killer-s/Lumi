-- Privacy: чи публікувати "слухає зараз" у публічному профілі.
ALTER TABLE "User" ADD COLUMN "showCurrentListening" BOOLEAN NOT NULL DEFAULT true;

-- Presence "слухає зараз" з TTL, що рахується від updatedAt.
CREATE TABLE "CurrentListening" (
    "userId" TEXT NOT NULL,
    "trackId" TEXT NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "CurrentListening_pkey" PRIMARY KEY ("userId")
);

CREATE INDEX "CurrentListening_trackId_idx" ON "CurrentListening"("trackId");
CREATE INDEX "CurrentListening_updatedAt_idx" ON "CurrentListening"("updatedAt");

ALTER TABLE "CurrentListening"
    ADD CONSTRAINT "CurrentListening_userId_fkey"
    FOREIGN KEY ("userId") REFERENCES "User"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "CurrentListening"
    ADD CONSTRAINT "CurrentListening_trackId_fkey"
    FOREIGN KEY ("trackId") REFERENCES "Track"("id")
    ON DELETE CASCADE ON UPDATE CASCADE;
