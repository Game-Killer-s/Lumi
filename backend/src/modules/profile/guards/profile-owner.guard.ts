import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';

import { AuthenticatedRequest } from '../../auth/types/authenticated-request.type';

type ProfileRequest = AuthenticatedRequest & {
  params?: { userId?: string };
};

// Захищає мутації профілю від редагування чужого акаунта.
@Injectable()
export class ProfileOwnerGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<ProfileRequest>();

    if (!request.user) {
      throw new UnauthorizedException();
    }

    const targetUserId = request.params?.userId;

    if (targetUserId && targetUserId !== request.user.sub) {
      throw new ForbiddenException('You can edit only your own profile');
    }

    return true;
  }
}
