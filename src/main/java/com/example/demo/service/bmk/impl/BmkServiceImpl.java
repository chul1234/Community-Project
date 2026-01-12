package com.example.demo.service.bmk.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.BmkDAO;
import com.example.demo.service.bmk.IBmkService;

@Service
public class BmkServiceImpl implements IBmkService {

    @Autowired
    private BmkDAO bmkDAO;

    @Override
    @Transactional
    public boolean toggleBookmark(String userId, String targetType, String targetId, String alias) {
        boolean exists = bmkDAO.exists(userId, targetType, targetId);
        if (exists) {
            bmkDAO.delete(userId, targetType, targetId);
            return false; // Removed
        } else {
            // Alias(이름)도 같이 저장
            bmkDAO.insert(userId, targetType, targetId, alias);
            return true; // Added
        }
    }

    @Override
    public boolean checkBookmark(String userId, String targetType, String targetId) {
        return bmkDAO.exists(userId, targetType, targetId);
    }

    @Override
    public List<Map<String, Object>> getMyBookmarks(String userId) {
        return bmkDAO.selectList(userId);
    }
}
