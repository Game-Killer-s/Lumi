import {
  applyDecorators,
  UseGuards,
} from '@nestjs/common';

import { Role } from '@prisma/client';

import {
  ApiBearerAuth,
  ApiForbiddenResponse,
  ApiUnauthorizedResponse,
} from '@nestjs/swagger';

import { RolesGuard } from '../guards/roles.guard';
import { Roles } from './roles.decorator';

export function Auth(
  ...roles: Role[]
) {
  return applyDecorators(
    ApiBearerAuth('access-token'),

    Roles(...roles),
    UseGuards(RolesGuard),

    ApiUnauthorizedResponse({
      description:
        'Access token is missing, invalid or expired',
    }),

    ApiForbiddenResponse({
      description:
        'The user does not have the required role',
    }),
  );
}