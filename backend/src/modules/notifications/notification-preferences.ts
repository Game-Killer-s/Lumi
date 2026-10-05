import { PushType } from './dto/send-push.dto';
import { UpdateNotificationPreferencesDto } from './dto/update-notification-preferences.dto';

// Ключі преференцій = ключі типів сповіщень.
export const NOTIFICATION_PREFERENCE_KEYS = [
  'new_release',
  'artist_update',
  'promo',
  'system',
] as const;

export type NotificationPreferenceKey =
  (typeof NOTIFICATION_PREFERENCE_KEYS)[number];

// Якій колонці в базі відповідає кожен ключ.
const PREFERENCE_FIELDS: Record<NotificationPreferenceKey, string> = {
  new_release: 'newReleases',
  artist_update: 'artistUpdates',
  promo: 'platformUpdates',
  system: 'systemNotifications',
};

// Дефолти: релізи і оновлення артистів — увімкнені, промо і системні теж,
// поки користувач сам не вимкне.
export const DEFAULT_NOTIFICATION_PREFERENCES: Record<
  NotificationPreferenceKey,
  boolean
> = {
  new_release: true,
  artist_update: true,
  promo: true,
  system: true,
};

// Тип сповіщення -> ключ преференцій.
const TYPE_PREFERENCES: Record<PushType, NotificationPreferenceKey> = {
  new_release: 'new_release',
  artist_update: 'artist_update',
  platform_update: 'promo',
  subscription: 'system',
};

export interface NotificationSettingsRow {
  newReleases: boolean;
  artistUpdates: boolean;
  platformUpdates: boolean;
  systemNotifications: boolean;
  pushEnabled: boolean;
}

export interface NotificationPreferencePatch {
  newReleases?: boolean;
  artistUpdates?: boolean;
  platformUpdates?: boolean;
  systemNotifications?: boolean;
}

export function toNotificationPreferences(
  settings: NotificationSettingsRow,
): Record<NotificationPreferenceKey, boolean> {
  return {
    new_release: settings.newReleases,
    artist_update: settings.artistUpdates,
    promo: settings.platformUpdates,
    system: settings.systemNotifications,
  };
}

// Ключі з запиту перекладаємо в колонки бази.
export function toSettingsPatch(
  dto: UpdateNotificationPreferencesDto,
): NotificationPreferencePatch {
  const patch: NotificationPreferencePatch = {};

  if (dto.new_release !== undefined) {
    patch.newReleases = dto.new_release;
  }

  if (dto.artist_update !== undefined) {
    patch.artistUpdates = dto.artist_update;
  }

  if (dto.promo !== undefined) {
    patch.platformUpdates = dto.promo;
  }

  if (dto.system !== undefined) {
    patch.systemNotifications = dto.system;
  }

  return patch;
}

// Чи можна слати користувачу сповіщення цього типу.
export function isNotificationTypeEnabled(
  settings: NotificationSettingsRow,
  type: PushType,
): boolean {
  if (!settings.pushEnabled) {
    return false;
  }

  return toNotificationPreferences(settings)[TYPE_PREFERENCES[type]];
}
