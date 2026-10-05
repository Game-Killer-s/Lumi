import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';
import type { Transporter } from 'nodemailer';

/**
 * MailService відправляє листи користувачам.
 *
 * У development, якщо SMTP не налаштовано, використовується
 * streamTransport nodemailer: реальний лист "відправляється",
 * але його вміст друкується в консоль сервера.
 * Це зручно для локальної демонстрації.
 */
@Injectable()
export class MailService {
  private readonly logger = new Logger(MailService.name);
  private readonly transporter: Transporter;
  private readonly from: string;

  constructor(
    private readonly configService: ConfigService,
  ) {
    this.from =
      this.configService.get<string>('mail.from') ??
      'Lumi <no-reply@lumi.app>';

    const host = this.configService.get<string>('mail.host');
    const user = this.configService.get<string>('mail.user');
    const pass = this.configService.get<string>('mail.pass');
    const port = this.configService.get<number>('mail.port') ?? 587;

    if (host && user && pass) {
      this.transporter = nodemailer.createTransport({
        host,
        port,
        secure: port === 465,
        auth: { user, pass },
      });
    } else {
      // Dev mode: друкуємо лист у консоль замість реального SMTP.
      this.transporter = nodemailer.createTransport({
        streamTransport: true,
        newline: 'unix',
        buffer: true,
      });
    }
  }

  /**
   * Надсилає лист із токеном для відновлення пароля.
   */
  async sendPasswordReset(
    to: string,
    token: string,
  ): Promise<void> {
    const message = {
      from: this.from,
      to,
      subject: 'Lumi — відновлення доступу',
      text: [
        `Привіт!`,
        ``,
        `Ви (або хтось інший) запросили відновлення пароля у Lumi.`,
        `Ваш токен для відновлення:`,
        ``,
        `  ${token}`,
        ``,
        `Токен дійсний протягом 1 години.`,
        `Якщо ви не робили цього запиту — просто проігноруйте цей лист.`,
      ].join('\n'),
      html: [
        `<p>Привіт!</p>`,
        `<p>Ви (або хтось інший) запросили відновлення пароля у <b>Lumi</b>.</p>`,
        `<p>Ваш токен для відновлення:</p>`,
        `<p style="font-size:18px;font-weight:bold;">${token}</p>`,
        `<p>Токен дійсний протягом 1 години.</p>`,
        `<p>Якщо ви не робили цього запиту — просто проігноруйте цей лист.</p>`,
      ].join(''),
    };

    const info = await this.transporter.sendMail(message);

    // streamTransport повертає сирий лист у info.message.
    const raw = (info as unknown as { message?: Buffer }).message;
    if (raw) {
      this.logger.log(
        `[dev mail] Password reset for ${to}:\n${raw.toString()}`,
      );
    }
  }
}
