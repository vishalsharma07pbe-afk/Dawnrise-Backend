package com.dawnrise.school.school.mapper;

import com.dawnrise.school.school.DTO.CreateSchoolRequest;
import com.dawnrise.school.school.DTO.SchoolOnboardingRequest;
import com.dawnrise.school.school.DTO.SchoolResponse;
import com.dawnrise.school.school.DTO.UpdateSchoolRequest;
import com.dawnrise.school.school.entity.School;
import org.springframework.stereotype.Component;

@Component
public class SchoolMapper {

    public School toEntity(CreateSchoolRequest request) {
        School school = new School();

        school.setSchoolCode(request.getSchoolCode());
        school.setName(request.getName());
        school.setEmail(request.getEmail());
        school.setPhone(request.getPhone());
        school.setAddress(request.getAddress());

        return school;
    }

    public School toEntity(SchoolOnboardingRequest request) {
        School school = new School();

        school.setSchoolCode(request.getSchoolCode());
        school.setName(request.getName());
        school.setEmail(request.getEmail());
        school.setPhone(request.getPhone());
        school.setAddress(request.getAddress());
        school.markProvisioningPending();

        return school;
    }

    public SchoolResponse toResponse(School school) {
        if(school == null) {
            return null;
        }

        return new SchoolResponse(
                school.getId(),
                school.getSchoolCode(),
                school.getName(),
                school.getEmail(),
                school.getPhone(),
                school.getAddress(),
                school.getStatus(),
                school.getCreatedAt(),
                school.getUpdatedAt()
        );
    }

    public void updateEntity(
            UpdateSchoolRequest request,
            School school
    ) {
        school.setName(request.getName());
        school.setEmail(request.getEmail());
        school.setPhone(request.getPhone());
        school.setAddress(request.getAddress());
    }
}
