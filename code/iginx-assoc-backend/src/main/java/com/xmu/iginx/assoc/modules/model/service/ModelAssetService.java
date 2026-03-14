package com.xmu.iginx.assoc.modules.model.service;

import com.xmu.iginx.assoc.modules.model.dto.ModelProfileUpdateRequest;
import com.xmu.iginx.assoc.modules.model.dto.ModelUploadRequest;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.vo.ModelFunctionOptionVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelProfileVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelSchemaParseVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelVersionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 模型资产服务接口。
 */
public interface ModelAssetService {

    /**
     * 获取模型档案列表。
     *
     * @return 档案列表
     */
    List<ModelProfileVO> listProfiles();

    /**
     * 获取指定档案详情。
     *
     * @param profileId 档案 ID
     * @return 档案详情
     */
    ModelProfileVO getProfile(Long profileId);

    /**
     * 上传模型文件并创建档案。
     *
     * @param request 上传请求
     * @param file 模型文件
     * @return 档案详情
     */
    ModelProfileVO uploadModel(ModelUploadRequest request, MultipartFile file);

    /**
     * 更新模型档案信息。
     *
     * @param profileId 档案 ID
     * @param request 更新请求
     */
    void updateProfile(Long profileId, ModelProfileUpdateRequest request);

    /**
     * 删除模型档案及其版本。
     *
     * @param profileId 档案 ID
     */
    void deleteProfile(Long profileId);

    /**
     * 删除指定版本。
     *
     * @param assetId 版本 ID
     */
    void deleteVersion(Long assetId);

    /**
     * 解析模型结构。
     *
     * @param file 模型文件
     * @return 结构信息
     */
    ModelVersionVO parseSchema(MultipartFile file);

    /**
     * 解析模型函数列表。
     *
     * @param file 模型文件
     * @return 函数列表
     */
    List<ModelFunctionOptionVO> listFunctions(MultipartFile file);

    /**
     * 按函数名解析模型结构。
     *
     * @param file 模型文件
     * @param functionName 函数名
     * @return 解析结果
     */
    ModelSchemaParseVO parseSchemaByFunction(MultipartFile file, String functionName);

    /**
     * 获取版本实体。
     *
     * @param assetId 版本 ID
     * @return 版本实体
     */
    ModelAssetEntity getAsset(Long assetId);
}
