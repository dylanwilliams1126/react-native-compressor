import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  // Image
  image_compress(imagePath: string, optionMap: Object): Promise<string>;
  // Video
  compress(fileUrl: string, optionMap: Object): Promise<string>;
  cancelCompression(uuid: string): void;
  getVideoMetaData(filePath: string): Promise<string>;
  activateBackgroundTask(options: Object): Promise<string>;
  deactivateBackgroundTask(options: Object): Promise<string>;
  //Audio
  compress_audio(fileUrl: string, optionMap: Object): Promise<string>;
  // Upload
  upload(fileUrl: string, options: Object): Promise<string>;
  // Download
  download(fileUrl: string, options: Object): Promise<string>;
  // Others
  generateFilePath(_extension: string): Promise<string>;
  getRealPath(path: string, type: string): Promise<string>;
  getFileSize(filePath: string): Promise<string>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Compressor');
