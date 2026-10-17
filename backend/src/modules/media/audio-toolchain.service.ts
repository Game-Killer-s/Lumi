import { Injectable, Logger } from '@nestjs/common';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

export interface LoudnessAnalysis {
  // Інтегрована гучність треку у LUFS
  integratedLufs: number;
  // Пікова гучність у dBTP
  truePeakDb: number;
}

// Один потік з відповіді ffprobe (нас цікавить аудіо-потік).
export interface AudioProbeStream {
  codec_type?: string;
  codec_name?: string;
  sample_rate?: string | number;
  channels?: number;
  bit_rate?: string;
  duration?: string;
  tags?: Record<string, string>;
}

// Сира відповідь ffprobe у форматі JSON (-print_format json).
export interface AudioProbeResult {
  streams: AudioProbeStream[];
  format?: {
    duration?: string;
    bit_rate?: string;
    tags?: Record<string, string>;
  };
}

/**
 * Розбір JSON-у, який друкує ffprobe.
 *
 * Винесено в чисту функцію: у тестах її годують реальними виводами ffprobe
 * для FLAC і WAV, тому перевірка не залежить від наявності бінарника.
 */
export function parseProbeOutput(stdout: string): AudioProbeResult {
  let parsed: unknown;

  try {
    parsed = JSON.parse(stdout);
  } catch {
    throw new Error('ffprobe output is not valid JSON');
  }

  if (typeof parsed !== 'object' || parsed === null) {
    throw new Error('ffprobe output is empty');
  }

  const { streams, format } = parsed as AudioProbeResult;

  return {
    streams: Array.isArray(streams) ? streams : [],
    format: format ?? undefined,
  };
}

/**
 * Обгортка над ffmpeg/loudnorm.
 *
 * Увесь зовнішній інструментарій зібраний в одному місці - тому в тестах
 * його можна підмінити (ffmpeg на машині розробника може бути відсутній).
 * ffmpeg має бути встановлений і доступний у PATH (або FFMPEG_PATH).
 */
@Injectable()
export class AudioToolchainService {
  private readonly logger = new Logger(AudioToolchainService.name);
  private readonly binary = process.env.FFMPEG_PATH || 'ffmpeg';
  private readonly probeBinary = process.env.FFPROBE_PATH || 'ffprobe';

  /**
   * Читає контейнер і потоки БЕЗ декодування: контейнер, кодек, sample rate,
   * канали, тривалість і embedded tags. Саме цим починається і завантаження,
   * і обробка - щоб пошкоджений файл не дійшов до транскодування.
   */
  async probe(inputPath: string): Promise<AudioProbeResult> {
    const { stdout } = await this.run(
      this.probeBinary,
      [
        '-v',
        'error',
        '-print_format',
        'json',
        '-show_format',
        '-show_streams',
        inputPath,
      ],
      'Audio probe failed',
    );

    return parseProbeOutput(stdout);
  }

  // Вимірює поточну гучність файлу (loudnorm у режимі аналізу).
  async analyzeLoudness(inputPath: string): Promise<LoudnessAnalysis> {
    const { stderr } = await this.run(this.binary, [
      '-hide_banner',
      '-i',
      inputPath,
      '-af',
      'loudnorm=print_format=json',
      '-f',
      'null',
      '-',
    ]);

    const json = this.lastJsonObject(stderr);

    if (!json) {
      throw new Error('ffmpeg did not return loudness information');
    }

    const parsed = JSON.parse(json) as { input_i: string; input_tp: string };

    return {
      integratedLufs: Number.parseFloat(parsed.input_i),
      truePeakDb: Number.parseFloat(parsed.input_tp),
    };
  }

  // Нормалізує гучність до цільового значення (true peak -1.5 dBTP).
  async normalizeToTarget(
    inputPath: string,
    outputPath: string,
    targetLufs: number,
  ): Promise<void> {
    await this.run(this.binary, [
      '-y',
      '-hide_banner',
      '-i',
      inputPath,
      '-af',
      `loudnorm=I=${targetLufs}:TP=-1.5:LRA=11`,
      outputPath,
    ]);
  }

  // Кодує у Opus із заданим бітрейтом (96/160/256 kbps).
  async transcodeToOpus(
    inputPath: string,
    outputPath: string,
    bitrateKbps: number,
  ): Promise<void> {
    await this.run(this.binary, [
      '-y',
      '-hide_banner',
      '-i',
      inputPath,
      '-c:a',
      'libopus',
      '-b:a',
      `${bitrateKbps}k`,
      outputPath,
    ]);
  }

  private async run(
    binary: string,
    args: string[],
    failureLabel = 'Media processing failed',
  ): Promise<{ stdout: string; stderr: string }> {
    try {
      const { stdout, stderr } = await execFileAsync(binary, args, {
        maxBuffer: 10 * 1024 * 1024,
      });

      return { stdout, stderr };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);

      this.logger.error(`${binary} ${args.join(' ')} failed: ${message}`);

      throw new Error(`${failureLabel}: ${message}`);
    }
  }

  // ffmpeg друкує JSON loudnorm у stderr - витягуємо останній блок {...}.
  private lastJsonObject(text: string): string | null {
    const end = text.lastIndexOf('}');

    if (end === -1) {
      return null;
    }

    const start = text.lastIndexOf('{', end);

    if (start === -1) {
      return null;
    }

    return text.slice(start, end + 1);
  }
}
