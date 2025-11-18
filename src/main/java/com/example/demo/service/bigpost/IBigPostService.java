package com.example.demo.service.bigpost;

import java.util.Map;

public interface IBigPostService {
    Map<String, Object> getBigPosts(int page, int size);
}