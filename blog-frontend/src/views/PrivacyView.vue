<script setup lang="ts">
import { onMounted } from 'vue'
import { setPageTitle } from '@/utils/seo'

onMounted(() => {
  setPageTitle('隐私政策')
})
</script>

<template>
  <article class="container narrow legal">
    <h1>隐私政策</h1>
    <p class="muted">最后更新：2026-11-05</p>

    <p>
      Eason Blog（以下简称“本站”）重视你的隐私。本政策说明我们收集哪些信息、
      如何使用与保护这些信息，以及你享有的权利。
    </p>

    <h2>一、我们收集的信息</h2>
    <ul>
      <li><strong>账号信息</strong>：注册时填写的邮箱、昵称、密码（加密存储）、可选头像与简介。</li>
      <li><strong>互动信息</strong>：你发表的评论、留言、收藏、点赞与邮件订阅状态。</li>
      <li><strong>访问日志</strong>：登录时间、IP 地址、浏览器标识等，用于安全审计与故障排查。</li>
      <li><strong>邮件信息</strong>：注册验证码、密码重置、订阅通知等邮件的发送记录。</li>
    </ul>

    <h2>二、信息的使用</h2>
    <ul>
      <li>提供登录、评论、留言、收藏等核心功能；</li>
      <li>发送你主动请求的验证码、密码重置邮件与新文章订阅通知；</li>
      <li>识别和防范垃圾评论、恶意登录等安全风险；</li>
      <li>统计站点访问情况，改进内容与体验。</li>
    </ul>
    <p>我们不会将你的个人信息用于与本站无关的广告推送或出售给第三方。</p>

    <h2>三、Cookie 与登录状态</h2>
    <p>
      登录状态通过 HttpOnly Cookie 保存，用于识别你的登录身份，有效期默认 24 小时。
      你可以随时退出登录或清除浏览器 Cookie；清除后需要重新登录。
    </p>

    <h2>四、信息的存储与安全</h2>
    <ul>
      <li>数据存储在本站自有的服务器与数据库中，仅站长可访问；</li>
      <li>密码使用 BCrypt 加密存储，无法被还原为明文；</li>
      <li>我们采取访问控制、日志审计等措施保护数据，但无法保证绝对安全。</li>
    </ul>

    <h2>五、信息的共享</h2>
    <p>
      除以下情况外，我们不会向第三方提供你的个人信息：
    </p>
    <ul>
      <li>你主动开启邮件订阅后，为发送邮件而使用 SMTP 邮件服务商；</li>
      <li>法律法规要求或有权机关依法调取；</li>
      <li>为保护本站、用户或公众的合法权益所必需。</li>
    </ul>

    <h2>六、信息的保留</h2>
    <p>
      账号与互动数据在账号存续期间保留；你删除评论或注销账号后，相关数据会被删除或匿名化。
      备份数据最长保留 14 天后自动清理。
    </p>

    <h2>七、你的权利</h2>
    <ul>
      <li>访问、更正你的个人资料；</li>
      <li>删除你发表的评论（发布后 3 分钟内且无回复）；</li>
      <li>随时关闭邮件订阅；</li>
      <li>申请注销账号并删除相关数据，可通过留言板或邮箱联系站长。</li>
    </ul>

    <h2>八、未成年人</h2>
    <p>
      本站主要面向成年技术爱好者。未成年人使用本站应在监护人指导下进行，
      并避免提交不必要的个人信息。
    </p>

    <h2>九、政策更新</h2>
    <p>
      我们可能根据功能变化更新本政策，更新后会在本页面公布并标注日期。
      继续使用本站即视为接受更新后的政策。
    </p>

    <h2>十、联系我们</h2>
    <p>
      如对本政策或个人信息处理有疑问，可通过本站留言板或邮箱
      <a href="mailto:3446885993@qq.com">3446885993@qq.com</a> 联系站长。
    </p>
  </article>
</template>

<style scoped>
.legal {
  max-width: 820px;
  padding: 32px 0 56px;
  line-height: 1.85;
  color: var(--fg-soft);
}

.legal h1 {
  margin: 0 0 6px;
  font-size: 28px;
  color: var(--fg);
}

.legal h2 {
  margin: 28px 0 8px;
  font-size: 17px;
  color: var(--fg);
}

.legal p,
.legal li {
  font-size: 14px;
}

.legal ul {
  padding-left: 22px;
}

.legal a {
  color: var(--accent);
}
</style>
