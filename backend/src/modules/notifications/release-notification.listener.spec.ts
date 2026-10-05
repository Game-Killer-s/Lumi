import { ReleaseEventBus } from './release.event-bus';
import { ReleaseNotificationListener } from './release-notification.listener';
import { ReleaseNotificationService } from './release-notification.service';

describe('ReleaseNotificationListener', () => {
  let listener: ReleaseNotificationListener;
  let eventBus: ReleaseEventBus;

  let service: { generateForRelease: jest.Mock };

  beforeEach(() => {
    eventBus = new ReleaseEventBus();
    service = { generateForRelease: jest.fn().mockResolvedValue({}) };

    listener = new ReleaseNotificationListener(
      eventBus,
      service as unknown as ReleaseNotificationService,
    );
  });

  it('generates notifications when the release.published event arrives', async () => {
    listener.onModuleInit();

    eventBus.emitReleasePublished({ releaseId: 'r1' });
    await tick();

    expect(service.generateForRelease).toHaveBeenCalledWith('r1');
  });

  it('does not crash the app when the generation fails', async () => {
    service.generateForRelease.mockRejectedValue(new Error('db is down'));
    listener.onModuleInit();

    eventBus.emitReleasePublished({ releaseId: 'r1' });
    await tick();

    expect(service.generateForRelease).toHaveBeenCalledWith('r1');
  });
});

function tick(): Promise<void> {
  return new Promise((resolve) => setImmediate(resolve));
}
