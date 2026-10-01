"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.QueueService = void 0;
const common_1 = require("@nestjs/common");
let QueueService = class QueueService {
    queue = {
        tracks: [
            { id: 'track-1', title: 'First Track', artist: 'Lumi Artist' },
            { id: 'track-2', title: 'Second Track', artist: 'Lumi Artist' },
            { id: 'track-3', title: 'Third Track', artist: 'Lumi Artist' },
        ],
        currentTrackId: 'track-1',
        total: 3,
        updatedAt: new Date().toISOString(),
    };
    getQueue() {
        return {
            ...this.queue,
            tracks: [...this.queue.tracks],
        };
    }
    setCurrentTrack(trackId) {
        const exists = this.queue.tracks.some((track) => track.id === trackId);
        if (!exists) {
            throw new common_1.NotFoundException('Track is not in the queue');
        }
        this.queue.currentTrackId = trackId;
        this.queue.updatedAt = new Date().toISOString();
        return this.getQueue();
    }
    addTrack(track) {
        this.queue.tracks.push(track);
        this.queue.total = this.queue.tracks.length;
        this.queue.updatedAt = new Date().toISOString();
        return this.getQueue();
    }
    removeTrack(trackId) {
        this.queue.tracks = this.queue.tracks.filter((track) => track.id !== trackId);
        this.queue.total = this.queue.tracks.length;
        if (this.queue.currentTrackId === trackId) {
            this.queue.currentTrackId = this.queue.tracks[0]?.id ?? null;
        }
        this.queue.updatedAt = new Date().toISOString();
        return this.getQueue();
    }
};
exports.QueueService = QueueService;
exports.QueueService = QueueService = __decorate([
    (0, common_1.Injectable)()
], QueueService);
