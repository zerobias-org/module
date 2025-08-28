import * as fs from 'fs';
import * as path from 'path';
import { enableNockRecording, stopNockRecording, FixtureData } from './nock-helpers';
import { sanitizeRecordedData } from './data-sanitizer';

export class RecordingManager {
  private recordingActive: boolean = false;
  private recordingName: string = '';
  private outputDir: string;
  
  constructor(outputDir: string = path.join(__dirname, '..', 'fixtures', 'recorded')) {
    this.outputDir = outputDir;
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }
  }
  
  startRecording(recordingName: string): void {
    if (this.recordingActive) {
      throw new Error('Recording already active. Stop current recording first.');
    }
    
    this.recordingName = recordingName;
    this.recordingActive = true;
    enableNockRecording();
  }
  
  stopRecording(): void {
    if (!this.recordingActive) {
      throw new Error('No active recording to stop.');
    }
    
    const recordings = stopNockRecording();
    this.recordingActive = false;
    
    if (recordings.length === 0) {
      console.warn(`No HTTP requests recorded for ${this.recordingName}`);
      return;
    }
    
    // Save raw recordings
    const rawPath = path.join(this.outputDir, `${this.recordingName}-raw.json`);
    fs.writeFileSync(rawPath, JSON.stringify(recordings, null, 2));
    
    // Sanitize and save clean recordings
    const sanitizedRecordings = recordings.map(recording => sanitizeRecordedData(recording));
    const sanitizedPath = path.join(this.outputDir, `${this.recordingName}.json`);
    fs.writeFileSync(sanitizedPath, JSON.stringify(sanitizedRecordings, null, 2));
    
    // Convert to fixture format and save templates
    const fixtures = this.convertToFixtures(sanitizedRecordings);
    fixtures.forEach((fixture, index) => {
      const fixturePath = path.join(__dirname, '..', 'fixtures', 'templates', `${this.recordingName}-${index}.json`);
      fs.writeFileSync(fixturePath, JSON.stringify(fixture, null, 2));
    });
    
    console.log(`Recording saved: ${recordings.length} requests recorded`);
    console.log(`Raw data: ${rawPath}`);
    console.log(`Sanitized data: ${sanitizedPath}`);
    console.log(`Fixtures generated: ${fixtures.length} files in templates/`);
  }
  
  private convertToFixtures(recordings: any[]): FixtureData[] {
    return recordings.map(recording => ({
      request: {
        url: recording.scope + recording.path,
        method: recording.method,
        headers: recording.reqheaders || {},
        body: recording.body
      },
      response: {
        status: recording.status || 200,
        headers: recording.headers || { 'content-type': 'application/json; charset=utf-8' },
        body: recording.response
      }
    }));
  }
  
  isRecording(): boolean {
    return this.recordingActive;
  }
  
  getCurrentRecordingName(): string {
    return this.recordingName;
  }
}