import { Role } from '@prisma/client';

export interface IssueTokenPairInput {
  userId: string;
  sessionId: string;
  familyId: string;
  refreshJti: string;
  role: Role;
}
