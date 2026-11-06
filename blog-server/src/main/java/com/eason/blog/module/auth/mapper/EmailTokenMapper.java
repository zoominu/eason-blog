package com.eason.blog.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eason.blog.module.auth.entity.EmailToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmailTokenMapper extends BaseMapper<EmailToken> {

    /** 原子消费：只有未使用过的 token 才能被标记，返回 1 表示本次抢到。 */
    @Update("UPDATE blog_email_token SET used = 1 WHERE id = #{id} AND used = 0")
    int markUsed(@Param("id") Long id);
}
