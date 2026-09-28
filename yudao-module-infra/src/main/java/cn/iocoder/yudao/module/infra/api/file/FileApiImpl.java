package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

/**
 * 文件 API 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class FileApiImpl implements FileApi {

    @Resource
    private FileService fileService;

    @Override
    public String createFile(byte[] content, String name, String directory, String type) {
        return fileService.createFile(content, name, directory, type);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        return fileService.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public byte[] getFileContent(Long fileId) throws Exception {
        FileDO file = fileService.getFile(fileId);
        if (file == null) {
            return null;
        }
        return fileService.getFileContent(file.getConfigId(), file.getPath());
    }

}
