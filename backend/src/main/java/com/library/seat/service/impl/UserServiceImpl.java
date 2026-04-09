package com.library.seat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.library.seat.entity.User;
import com.library.seat.exception.BusinessException;
import com.library.seat.mapper.UserMapper;
import com.library.seat.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public Page<User> listUsers(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword);
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> userPage = userMapper.selectPage(new Page<>(page, size), wrapper);
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return userPage;
    }

    @Override
    public void toggleUserStatus(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getRole() == 1) {
            throw new BusinessException("不能操作管理员账号");
        }
        user.setDeleted(user.getDeleted() == 0 ? 1 : 0);
        userMapper.updateById(user);
        log.info("切换用户状态: userId={}, deleted={}", id, user.getDeleted());
    }
}
