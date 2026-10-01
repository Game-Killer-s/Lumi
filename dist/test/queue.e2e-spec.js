"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const testing_1 = require("@nestjs/testing");
const supertest_1 = __importDefault(require("supertest"));
const app_module_1 = require("../src/app.module");
describe('Playback queue (e2e)', () => {
    let app;
    beforeAll(async () => {
        const moduleRef = await testing_1.Test.createTestingModule({
            imports: [app_module_1.AppModule],
        }).compile();
        app = moduleRef.createNestApplication();
        await app.init();
    });
    afterAll(async () => {
        await app.close();
    });
    it('returns the current playback queue', async () => {
        const response = await (0, supertest_1.default)(app.getHttpServer())
            .get('/queue')
            .expect(200);
        expect(response.body.total).toBe(3);
        expect(response.body.tracks).toHaveLength(3);
        expect(response.body.currentTrackId).toBe('track-1');
    });
    it('changes the current track', async () => {
        const response = await (0, supertest_1.default)(app.getHttpServer())
            .post('/queue/current/track-2')
            .expect(201);
        expect(response.body.currentTrackId).toBe('track-2');
    });
});
