import { Transform } from 'class-transformer';
import { IsString, Length } from 'class-validator';

// Тіло POST /api/tracks/:trackId/comments.
// Ліміт 500 символів збігається з клієнтом (CommentsRules.MAX_LENGTH).
export class CreateCommentDto {
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  @IsString()
  @Length(1, 500)
  text: string;
}
