#import <Accelerate/Accelerate.h>
#import <CoreGraphics/CoreGraphics.h>

#import "ImageCompressor.h"

@implementation ImageCompressor
+ (CGSize) findTargetSize: (UIImage *) image maxWidth: (int) maxWidth maxHeight: (int) maxHeight {
    CGFloat width = image.size.width;
    CGFloat height = image.size.height;
    
    if (width > height) {
        CGFloat newHeight = height / (width / maxWidth);
        return CGSizeMake(maxWidth, newHeight);
    }
    
    CGFloat newWidth = width / (height / maxHeight);
    return CGSizeMake(newWidth, maxHeight);
}

+(UIImage *) decodeImage: (NSString *) value {
    NSData *data = [[NSData alloc] initWithBase64EncodedString:value options: NSDataBase64DecodingIgnoreUnknownCharacters];
    return [[UIImage alloc] initWithData:data];
}

+(UIImage *) loadImage:(NSString *)path {
    UIImage *image=nil;
    if ([path hasPrefix:@"data:"] || [path hasPrefix:@"file:"]) {
            NSURL *imageUrl = [[NSURL alloc] initWithString:path];
            image = [UIImage imageWithData:[NSData dataWithContentsOfURL:imageUrl]];
          } else {
            image = [[UIImage alloc] initWithContentsOfFile:path];
          }
    return image;
}


+(UIImage *)resize:(UIImage *)image maxWidth:(int)maxWidth maxHeight:(int)maxHeight {
    CGSize targetSize = [self findTargetSize:image maxWidth:maxWidth maxHeight:maxHeight];
    
    CGImageRef cgImage = image.CGImage;
    
    int sourceWidth = image.size.width;
    int sourceHeight = image.size.height;
    int targetWidth = targetSize.width;
    int targetHeight = targetSize.height;
    int bytesPerPixel = 4;
    int sourceBytesPerRow = sourceWidth * bytesPerPixel;
    int targetBytesPerRow = targetWidth * bytesPerPixel;
    int bitsPerComponent = 8;
    
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    
    unsigned char *sourceData = (unsigned char*)calloc(sourceHeight * sourceWidth * bytesPerPixel, sizeof(unsigned char));
    
    CGContextRef context = CGBitmapContextCreate(sourceData, sourceWidth, sourceHeight,
                                                 bitsPerComponent, sourceBytesPerRow, colorSpace,
                                                 kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Big);
    CGContextDrawImage(context, CGRectMake(0, 0, sourceWidth, sourceHeight), cgImage);
    CGContextRelease(context);
    
    unsigned char *targetData = (unsigned char*)calloc(targetWidth * targetHeight * bytesPerPixel, sizeof(unsigned char));
    
    vImage_Buffer srcBuffer = {
        .data = sourceData,
        .width = sourceWidth,
        .height = sourceHeight,
        .rowBytes = sourceBytesPerRow
    };
    vImage_Buffer targetBuffer = {
        .data = targetData,
        .width = targetWidth,
        .height = targetHeight,
        .rowBytes = targetBytesPerRow
    };
    
    vImage_Error error = vImageScale_ARGB8888(&srcBuffer, &targetBuffer, nil, kvImageHighQualityResampling);
    free(sourceData);
    if (error != kvImageNoError) {
        free(targetData);
        NSException *exception = [[NSException alloc] initWithName: @"drawing_erro" reason:@"Problem while rendering your image" userInfo:nil];
        @throw exception;
    }
    
    CGContextRef targetContext = CGBitmapContextCreate(targetData, targetWidth, targetHeight,
                                                     bitsPerComponent, targetBytesPerRow, colorSpace,
                                                     kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Big);
    CGImageRef targetRef = CGBitmapContextCreateImage(targetContext);
    
    UIImage* resizedImage = [UIImage imageWithCGImage:targetRef];
    
    CGImageRelease(targetRef);
    CGColorSpaceRelease(colorSpace);
    CGContextRelease(targetContext);
    
    
    free(targetData);
    
    return resizedImage;
}

+(NSString *)compress:(UIImage *)image output:(enum OutputType)output quality:(float)quality outputExtension:(NSString*)outputExtension isBase64:(Boolean)isBase64{
    NSData *data;
    NSException *exception;
    
    switch (output) {
        case jpg:
            data = UIImageJPEGRepresentation(image, quality);
            break;
        case png:
            data = UIImagePNGRepresentation(image);
            break;
        default:
            exception = [[NSException alloc] initWithName: @"unsupported_format" reason:@"This format is not supported." userInfo:nil];
            @throw exception;
    }
  
    if(isBase64)
    {
        return [data base64EncodedStringWithOptions:0];
    }
    else
    {
        NSString *filePath =[self generateCacheFilePath:outputExtension];
        [data writeToFile:filePath atomically:YES];
        return [NSString stringWithFormat: @"file:/%@", filePath];
    }
}

+ (NSString *)generateCacheFilePath:(NSString *)extension{
    NSUUID *uuid = [NSUUID UUID];
    NSString *imageNameWihtoutExtension = [uuid UUIDString];
    NSString *imageName=[imageNameWihtoutExtension stringByAppendingPathExtension:extension];
    NSString *filePath =
        [NSTemporaryDirectory() stringByAppendingPathComponent:imageName];
    return filePath;
}

@end
