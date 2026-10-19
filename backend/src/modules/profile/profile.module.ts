import { Module } from '@nestjs/common';

import { AuthModule } from '../auth/auth.module';
import { MediaModule } from '../media/media.module';
import { CurrentListeningService } from './current-listening.service';
import { PresenceRateLimitGuard } from './guards/presence-rate-limit.guard';
import { ProfileOwnerGuard } from './guards/profile-owner.guard';
import { PresenceRateLimiter } from './presence-rate-limiter';
import { ProfileController } from './profile.controller';
import { ProfileService } from './profile.service';
import { PublicProfileController } from './public-profile.controller';

// Профіль, приватність і presence поточного користувача.
@Module({
  imports: [AuthModule, MediaModule],
  controllers: [ProfileController, PublicProfileController],
  providers: [
    ProfileService,
    CurrentListeningService,
    PresenceRateLimiter,
    PresenceRateLimitGuard,
    ProfileOwnerGuard,
  ],
})
export class ProfileModule {}
