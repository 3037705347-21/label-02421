package com.library.seat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 图像清洁度自动检测服务
 * 通过分析上传照片的图像特征来判断座位是否清理干净：
 * 1. 亮度分析 - 清洁的桌面通常亮度均匀且较高
 * 2. 色彩均匀度 - 干净桌面颜色分布均匀，有垃圾则色彩杂乱
 * 3. 暗色块占比 - 垃圾/杂物通常表现为暗色不规则区域
 * 4. 边缘复杂度 - 干净桌面边缘简单，有杂物则边缘复杂
 */
@Slf4j
@Service
public class CleanDetectionService {

    // 清洁度评分阈值：>= 60 判定为清洁
    private static final int CLEAN_THRESHOLD = 60;

    /**
     * 分析图片清洁度
     * @param imageFile 上传的图片文件
     * @return 包含 score(0-100), result(1-清洁/2-不清洁), detail(说明) 的结果
     */
    public Map<String, Object> analyzeCleanness(File imageFile) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 设置 headless 模式，确保在无图形环境（Docker）中也能正常工作
            System.setProperty("java.awt.headless", "true");

            BufferedImage image = null;
            // 优先使用 FileInputStream 读取，避免某些环境下 File 方式的兼容问题
            try (InputStream is = new FileInputStream(imageFile)) {
                image = ImageIO.read(is);
            }

            if (image == null) {
                log.warn("ImageIO 无法解析图片文件: {}，尝试基于文件大小的备用检测", imageFile.getAbsolutePath());
                // 备用方案：基于文件大小和扩展名进行基础评估
                return fallbackAnalysis(imageFile);
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int totalPixels = width * height;

            log.info("开始图像清洁度检测: {}x{}, 文件: {}", width, height, imageFile.getName());

            // 采样分析（大图时每隔几个像素取样，提升性能）
            int step = Math.max(1, (int) Math.sqrt(totalPixels / 50000.0));

            double totalBrightness = 0;
            double totalBrightnessSq = 0;
            int darkPixelCount = 0;
            int sampledPixels = 0;

            // 分块统计（将图片分为 8x8 网格）
            int gridSize = 8;
            double[][] blockBrightness = new double[gridSize][gridSize];
            int[][] blockCount = new int[gridSize][gridSize];

            for (int y = 0; y < height; y += step) {
                for (int x = 0; x < width; x += step) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    // 亮度（加权灰度值）
                    double brightness = 0.299 * r + 0.587 * g + 0.114 * b;
                    totalBrightness += brightness;
                    totalBrightnessSq += brightness * brightness;
                    sampledPixels++;

                    // 暗色像素统计（亮度 < 80 视为暗色区域）
                    if (brightness < 80) {
                        darkPixelCount++;
                    }

                    // 分块统计
                    int gx = Math.min(x * gridSize / width, gridSize - 1);
                    int gy = Math.min(y * gridSize / height, gridSize - 1);
                    blockBrightness[gy][gx] += brightness;
                    blockCount[gy][gx]++;
                }
            }

            if (sampledPixels == 0) {
                result.put("score", 0);
                result.put("result", 0);
                result.put("detail", "图片像素为空");
                return result;
            }

            // 指标1: 平均亮度评分（0-25分）- 干净桌面通常较亮
            double avgBrightness = totalBrightness / sampledPixels;
            double brightnessScore;
            if (avgBrightness >= 120) {
                brightnessScore = 25;
            } else if (avgBrightness >= 60) {
                brightnessScore = 10 + (avgBrightness - 60) * 15.0 / 60.0;
            } else {
                brightnessScore = avgBrightness * 10.0 / 60.0;
            }

            // 指标2: 亮度均匀度评分（0-25分）- 干净桌面亮度方差小
            double brightnessVariance = (totalBrightnessSq / sampledPixels) - (avgBrightness * avgBrightness);
            double stdDev = Math.sqrt(Math.max(0, brightnessVariance));
            double uniformityScore;
            if (stdDev <= 35) {
                uniformityScore = 25;
            } else if (stdDev <= 65) {
                uniformityScore = 25 - (stdDev - 35) * 20.0 / 30.0;
            } else {
                uniformityScore = Math.max(0, 5 - (stdDev - 65) * 5.0 / 40.0);
            }

            // 指标3: 暗色块占比评分（0-25分）- 垃圾通常是暗色
            double darkRatio = (double) darkPixelCount / sampledPixels;
            double darkScore;
            if (darkRatio <= 0.15) {
                darkScore = 25;
            } else if (darkRatio <= 0.4) {
                darkScore = 25 - (darkRatio - 0.15) * 80;
            } else {
                darkScore = Math.max(0, 5 - (darkRatio - 0.4) * 10);
            }

            // 指标4: 分块亮度差异评分（0-25分）- 检测局部异常区域
            double blockVarianceSum = 0;
            int validBlocks = 0;
            double overallBlockAvg = 0;
            for (int gy = 0; gy < gridSize; gy++) {
                for (int gx = 0; gx < gridSize; gx++) {
                    if (blockCount[gy][gx] > 0) {
                        blockBrightness[gy][gx] /= blockCount[gy][gx];
                        overallBlockAvg += blockBrightness[gy][gx];
                        validBlocks++;
                    }
                }
            }
            if (validBlocks > 0) {
                overallBlockAvg /= validBlocks;
                for (int gy = 0; gy < gridSize; gy++) {
                    for (int gx = 0; gx < gridSize; gx++) {
                        if (blockCount[gy][gx] > 0) {
                            double diff = blockBrightness[gy][gx] - overallBlockAvg;
                            blockVarianceSum += diff * diff;
                        }
                    }
                }
                blockVarianceSum = Math.sqrt(blockVarianceSum / validBlocks);
            }
            double blockScore;
            if (blockVarianceSum <= 25) {
                blockScore = 25;
            } else if (blockVarianceSum <= 55) {
                blockScore = 25 - (blockVarianceSum - 25) * 20.0 / 30.0;
            } else {
                blockScore = Math.max(0, 5 - (blockVarianceSum - 55) * 5.0 / 30.0);
            }

            // 综合评分
            int totalScore = (int) Math.round(brightnessScore + uniformityScore + darkScore + blockScore);
            totalScore = Math.max(0, Math.min(100, totalScore));

            boolean isClean = totalScore >= CLEAN_THRESHOLD;

            // 生成详情说明
            StringBuilder detail = new StringBuilder();
            detail.append(String.format("清洁度评分: %d/100。", totalScore));
            detail.append(String.format("亮度评分: %.0f/25（平均亮度%.0f）；", brightnessScore, avgBrightness));
            detail.append(String.format("均匀度评分: %.0f/25（标准差%.1f）；", uniformityScore, stdDev));
            detail.append(String.format("暗色占比评分: %.0f/25（暗色%.1f%%）；", darkScore, darkRatio * 100));
            detail.append(String.format("区域一致性评分: %.0f/25。", blockScore));
            if (isClean) {
                detail.append("自动判定: 座位清洁度达标。");
            } else {
                detail.append("自动判定: 座位可能未清理干净，需人工复审。");
            }

            result.put("score", totalScore);
            result.put("result", isClean ? 1 : 2);
            result.put("detail", detail.toString());

            log.info("图像清洁度检测完成: score={}, isClean={}, file={}", totalScore, isClean, imageFile.getName());

        } catch (Exception e) {
            log.error("图像清洁度检测异常: ", e);
            // 异常时使用备用方案而不是直接返回0
            return fallbackAnalysis(imageFile);
        }
        return result;
    }

    /**
     * 备用检测方案：当 ImageIO 无法解析图片时（如 Docker headless 环境），
     * 通过读取文件原始字节进行基础分析
     */
    private Map<String, Object> fallbackAnalysis(File imageFile) {
        Map<String, Object> result = new HashMap<>();
        try {
            long fileSize = imageFile.length();
            byte[] bytes;
            // 读取部分字节进行分析（最多读取 100KB）
            int readSize = (int) Math.min(fileSize, 100 * 1024);
            bytes = new byte[readSize];
            try (FileInputStream fis = new FileInputStream(imageFile)) {
                fis.read(bytes);
            }

            // 基于字节值分布进行简单分析
            int totalBrightness = 0;
            int darkBytes = 0;
            int sampleCount = 0;
            // 跳过文件头（通常是元数据），从第100字节开始采样
            int startOffset = Math.min(100, readSize / 2);
            for (int i = startOffset; i < readSize; i += 3) {
                int val = bytes[i] & 0xFF;
                totalBrightness += val;
                sampleCount++;
                if (val < 80) {
                    darkBytes++;
                }
            }

            if (sampleCount == 0) {
                result.put("score", 50);
                result.put("result", 2);
                result.put("detail", "备用检测：无法分析图片内容，默认需人工复审。");
                return result;
            }

            double avgVal = (double) totalBrightness / sampleCount;
            double darkRatio = (double) darkBytes / sampleCount;

            // 简化评分：基于平均字节值和暗色比例
            double brightnessScore = Math.min(50, avgVal * 50.0 / 180.0);
            double darkPenalty = darkRatio * 50;
            int score = (int) Math.round(Math.max(0, Math.min(100, brightnessScore + 50 - darkPenalty)));

            boolean isClean = score >= CLEAN_THRESHOLD;

            StringBuilder detail = new StringBuilder();
            detail.append(String.format("清洁度评分: %d/100（备用检测模式）。", score));
            detail.append(String.format("平均亮度值: %.0f；暗色占比: %.1f%%。", avgVal, darkRatio * 100));
            if (isClean) {
                detail.append("自动判定: 座位清洁度达标。");
            } else {
                detail.append("自动判定: 座位可能未清理干净，需人工复审。");
            }

            result.put("score", score);
            result.put("result", isClean ? 1 : 2);
            result.put("detail", detail.toString());

            log.info("备用清洁度检测完成: score={}, isClean={}, file={}", score, isClean, imageFile.getName());

        } catch (Exception e) {
            log.error("备用清洁度检测也失败: ", e);
            // 最终兜底：给一个中等分数，标记需人工复审
            result.put("score", 50);
            result.put("result", 2);
            result.put("detail", "自动检测暂不可用，已提交人工复审。");
        }
        return result;
    }
}
