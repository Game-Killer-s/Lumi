import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class UpdateCurrentListeningDto {
  @ApiProperty({ example: '5d1c2b3a-0000-4000-8000-000000000001' })
  @IsString()
  @IsNotEmpty()
  @MaxLength(64)
  trackId!: string;
}

export class CurrentListeningTrackDto {
  @ApiProperty()
  id!: string;

  @ApiProperty()
  title!: string;

  @ApiProperty()
  duration!: number;

  @ApiProperty({ nullable: true, type: String })
  coverUrl!: string | null;

  @ApiProperty()
  artistName!: string;
}

export class CurrentListeningDto {
  @ApiProperty({ type: CurrentListeningTrackDto })
  track!: CurrentListeningTrackDto;

  @ApiProperty({ type: String, format: 'date-time' })
  updatedAt!: Date;

  @ApiProperty({ type: String, format: 'date-time' })
  expiresAt!: Date;
}

// Відповідь власнику: статус збережено, навіть якщо privacy його приховує.
export class CurrentListeningStatusDto {
  @ApiProperty()
  trackId!: string;

  @ApiProperty({ type: String, format: 'date-time' })
  updatedAt!: Date;

  @ApiProperty({ type: String, format: 'date-time' })
  expiresAt!: Date;

  @ApiProperty({ example: true })
  visible!: boolean;
}
