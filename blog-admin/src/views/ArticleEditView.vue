<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Promotion } from '@element-plus/icons-vue'
import { articleApi, categoryApi, tagApi } from '@/api'
import type { ArticleStatus, Category, Tag } from '@/api/types'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

const route = useRoute()
const router = useRouter()

const articleId = computed(() => {
  const id = Number(route.params.id)
  return Number.isFinite(id) && id > 0 ? id : null
})
const isEdit = computed(() => articleId.value !== null)

const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)
const saving = ref(false)
const status = ref<ArticleStatus>(0)

const form = reactive({
  title: '',
  slug: '',
  categoryId: undefined as number | undefined,
  tagIds: [] as number[],
  summary: '',
  cover: '',
  content: '',
  visibility: 0,
  isTop: 0,
  isRecommend: 0,
  seoTitle: '',
  seoDescription: ''
})

const statusText = computed(() => {
  if (!isEdit.value) return '新建文章'
  return { 0: '草稿', 1: '已发布', 2: '已下线', 3: '回收站' }[status.value]
})

function validate(): boolean {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return false
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写正文')
    return false
  }
  return true
}

async function save(publish: boolean) {
  if (!validate()) return
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      slug: form.slug.trim() || undefined,
      categoryId: form.categoryId ?? null,
      summary: form.summary || undefined,
      cover: form.cover || undefined,
      content: form.content,
      visibility: form.visibility,
      isTop: form.isTop,
      isRecommend: form.isRecommend,
      seoTitle: form.seoTitle || undefined,
      seoDescription: form.seoDescription || undefined,
      tagIds: form.tagIds,
      status: publish ? 1 : 0
    }

    if (isEdit.value) {
      await articleApi.update(articleId.value!, payload)
      if (publish) {
        await articleApi.publish(articleId.value!)
      }
      ElMessage.success(publish ? '已保存并发布' : '已保存')
      await load()
    } else {
      const { id } = await articleApi.create(payload)
      ElMessage.success(publish ? '已创建并发布' : '草稿已保存')
      await router.replace(`/articles/${id}/edit`)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function load() {
  if (!articleId.value) return
  loading.value = true
  try {
    const article = await articleApi.detail(articleId.value)
    form.title = article.title
    form.slug = article.slug
    form.categoryId = article.categoryId ?? undefined
    form.tagIds = article.tagIds ?? []
    form.summary = article.summary ?? ''
    form.cover = article.cover ?? ''
    form.content = article.content ?? ''
    form.visibility = article.visibility ?? 0
    form.isTop = article.top ? 1 : 0
    form.isRecommend = article.recommend ? 1 : 0
    form.seoTitle = article.seoTitle ?? ''
    form.seoDescription = article.seoDescription ?? ''
    status.value = article.status
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function back() {
  await router.push('/articles')
}

onMounted(async () => {
  try {
    const [cats, tagList] = await Promise.all([categoryApi.list(), tagApi.list()])
    categories.value = cats
    tags.value = tagList
  } catch {
    // 选项加载失败时仍可编辑正文
  }
  await load()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="page__head">
      <div class="head-left">
        <el-button text :icon="ArrowLeft" @click="back">返回列表</el-button>
        <div>
          <h2 class="page__title">{{ isEdit ? '编辑文章' : '新建文章' }}</h2>
          <p class="page__desc">状态：{{ statusText }} · 正文以 Markdown 保存</p>
        </div>
      </div>
      <div class="head-actions">
        <el-button :loading="saving" :icon="Check" @click="save(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" :icon="Promotion" @click="save(true)">保存并发布</el-button>
      </div>
    </header>

    <div class="layout">
      <el-card shadow="never" class="panel">
        <el-form label-position="top">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="一句话说清这篇写什么" />
          </el-form-item>

          <el-form-item label="Slug（URL 标识）">
            <el-input v-model="form.slug" placeholder="留空按标题自动生成；中文标题会生成 post-日期-随机串" />
            <p class="hint">公开地址：/article/{{ form.slug || '（保存后生成）' }}</p>
          </el-form-item>

          <el-form-item label="正文" required>
            <MarkdownEditor v-model="form.content" />
          </el-form-item>
        </el-form>
      </el-card>

      <aside class="side">
        <el-card shadow="never" class="panel side-card">
          <template #header><span class="side-title">发布设置</span></template>
          <el-form label-position="top">
            <el-form-item label="分类">
              <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 100%">
                <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>

            <el-form-item label="标签">
              <el-select
                v-model="form.tagIds"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择标签"
                style="width: 100%"
              >
                <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>

            <el-form-item label="可见性">
              <el-radio-group v-model="form.visibility">
                <el-radio :value="0">公开</el-radio>
                <el-radio :value="1">私密</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="置顶 / 推荐">
              <div class="switches">
                <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" active-text="置顶" />
                <el-switch v-model="form.isRecommend" :active-value="1" :inactive-value="0" active-text="推荐" />
              </div>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="panel side-card">
          <template #header><span class="side-title">摘要与 SEO</span></template>
          <el-form label-position="top">
            <el-form-item label="摘要">
              <el-input
                v-model="form.summary"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="留空时列表页会从正文截取"
              />
            </el-form-item>
            <el-form-item label="封面图地址">
              <el-input v-model="form.cover" placeholder="可留空" />
            </el-form-item>
            <el-form-item label="SEO 标题">
              <el-input v-model="form.seoTitle" maxlength="200" placeholder="默认使用文章标题" />
            </el-form-item>
            <el-form-item label="SEO 描述">
              <el-input v-model="form.seoDescription" maxlength="500" placeholder="默认使用摘要" />
            </el-form-item>
          </el-form>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head-left {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.panel {
  border-radius: 12px;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-card {
  margin-bottom: 0;
}

.side-title {
  font-size: 13px;
  font-weight: 500;
}

.hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--admin-muted);
}

.switches {
  display: flex;
  gap: 16px;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
