import { IsString, MaxLength, MinLength } from 'class-validator';

export class TwoFactorChallengeDto {
  @IsString()
  challengeId!: string;

  @IsString()
  @MinLength(6)
  @MaxLength(64)
  code!: string;
}
