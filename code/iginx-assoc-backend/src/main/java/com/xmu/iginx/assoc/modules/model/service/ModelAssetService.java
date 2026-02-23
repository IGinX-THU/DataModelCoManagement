package com.xmu.iginx.assoc.modules.model.service;

import com.xmu.iginx.assoc.modules.model.dto.ModelProfileUpdateRequest;
import com.xmu.iginx.assoc.modules.model.dto.ModelUploadRequest;
import com.xmu.iginx.assoc.modules.model.entity.ModelAssetEntity;
import com.xmu.iginx.assoc.modules.model.vo.ModelProfileVO;
import com.xmu.iginx.assoc.modules.model.vo.ModelVersionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ModelAssetService {

    List<ModelProfileVO> listProfiles();

    ModelProfileVO getProfile(Long profileId);

    ModelProfileVO uploadModel(ModelUploadRequest request, MultipartFile file);

    void updateProfile(Long profileId, ModelProfileUpdateRequest request);

    void deleteProfile(Long profileId);

    void deleteVersion(Long assetId);

    ModelVersionVO parseSchema(MultipartFile file);

    ModelAssetEntity getAsset(Long assetId);
}
