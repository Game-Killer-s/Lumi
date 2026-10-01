"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const common_1 = require("@nestjs/common");
const queue_service_1 = require("./queue.service");
describe('QueueService', () => {
    let service;
    beforeEach(() => {
        service = new queue_service_1.QueueService();
    });
    it('returns tracks, current track and total count', () => {
        const queue = service.getQueue();
        expect(queue.tracks).toHaveLength(3);
        expect(queue.currentTrackId).toBe('track-1');
        expect(queue.total).toBe(3);
    });
    it('changes the current track', () => {
        const queue = service.setCurrentTrack('track-2');
        expect(queue.currentTrackId).toBe('track-2');
        expect(queue.updatedAt).toBeTruthy();
    });
    it('rejects a track that is not in the queue', () => {
        expect(() => service.setCurrentTrack('unknown')).toThrow(common_1.NotFoundException);
    });
    it('updates total after adding a track', () => {
        const queue = service.addTrack({
            id: 'track-4',
            title: 'Fourth Track',
            artist: 'Lumi Artist',
        });
        expect(queue.total).toBe(4);
    });
});
