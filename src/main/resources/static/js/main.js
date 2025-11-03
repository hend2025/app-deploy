$(document).ready(function() {
    // 存储每个tab的行数，用于增量加载
    window.tabLineCounts = {};
    
    // 存储每个tab的滚动状态，用于判断用户是否手动滚动
    window.tabScrollStates = {};
    
    // 自动刷新控制 默认为true
    window.autoRefreshEnabled = localStorage.getItem('autoRefreshEnabled') !== 'false';
    window.autoRefreshInterval = null;
    window.maxLines = parseInt(localStorage.getItem('maxLines')) || 2000;

    // 初始化 Select2 可过滤下拉框
    $('#fileSelect').select2({
        theme: 'bootstrap-5',
        placeholder: '请选择或搜索日志文件...',
        allowClear: true,
        width: '100%',
        language: {
            noResults: function() {
                return "未找到匹配的文件";
            },
            searching: function() {
                return "搜索中...";
            }
        }
    });

    // 文件选择变化事件
    $('#fileSelect').on('select2:select select2:clear', function() {
        const selectedFile = $(this).val();
        if (selectedFile) {
            createOrSwitchToTab(selectedFile);
            $('#downloadBtn').prop('disabled', false);
            $('#scrollToBottomBtn').prop('disabled', false);
            $('#clearBtn').prop('disabled', false);
        } else {
            showEmptyState();
            $('#downloadBtn').prop('disabled', true);
            $('#scrollToBottomBtn').prop('disabled', true);
            $('#clearBtn').prop('disabled', true);
        }
    });
    
    // 下载按钮点击事件
    $('#downloadBtn').click(function() {
        const selectedFile = $('#fileSelect').val();
        if (selectedFile) {
            downloadLogFile(selectedFile);
        } else {
            showNotification('请先选择一个文件', 'warning');
        }
    });
    
    // 滚动到底部按钮点击事件
    $('#scrollToBottomBtn').click(function() {
        const activeTab = $('.tab-pane.active');
        if (activeTab.length > 0) {
            const $fileContent = activeTab.find('.file-content');
            
            // 使用最简单直接的滚动方法
            forceScrollToBottomSimple();
            
            // 重置滚动状态
            const tabId = activeTab.attr('id');
            const scrollState = window.tabScrollStates[tabId];
            if (scrollState) {
                scrollState.isUserScrolling = false;
                scrollState.isAtBottom = true;
            }
        } else {
            showNotification('请先选择一个文件', 'warning');
        }
    });
    
    // 清空按钮点击事件
    $('#clearBtn').click(function() {
        const activeTab = $('.tab-pane.active');
        if (activeTab.length > 0) {
            const $fileContent = activeTab.find('.file-content');
            if ($fileContent.length > 0) {
                // 清空内容 - 直接清空textarea或整个容器
                const $textarea = $fileContent.find('textarea');
                if ($textarea.length > 0) {
                    $textarea.val('');
                } else {
                    $fileContent.html('<div class="text-muted">内容已清空</div>');
                }
                showNotification('内容已清空', 'info');
            } else {
                showNotification('未找到可清空的内容', 'warning');
            }
        } else {
            showNotification('请先选择一个文件', 'warning');
        }
    });
    
    // 自动刷新控制按钮点击事件
    $('#autoRefreshBtn').click(function() {
        toggleAutoRefresh();
    });
    
    
    // 最大行数下拉框变化事件
    $('#maxLines').change(function() {
        const newMaxLines = parseInt($(this).val());
        updateMaxLines(newMaxLines);
    });
         
    // 初始化自动刷新按钮状态
    initializeAutoRefreshButton();
    
    // 初始化最大行数下拉框
    initializeMaxLines();
    
    // 加载日志文件列表
    loadLogFiles();
    
    // 启动自动刷新
    startAutoRefresh();
    
    // 监听tab切换事件
    $('body').on('shown.bs.tab', 'button[data-bs-toggle="tab"]', function (e) {
        const targetTabId = $(e.target).attr('data-bs-target').substring(1);
        
        // 更新fileSelect的值以匹配当前活动tab
        const activeTab = $('#' + targetTabId);
        if (activeTab.length > 0) {
            const fileName = activeTab.data('fileName');
            if (fileName) {
                $('#fileSelect').val(fileName).trigger('change.select2');
            }
        }
        
        // 更新当前活动tab的滚动状态
        setTimeout(function() {
            const $fileContent = $('#' + targetTabId + ' .file-content');
            const $textarea = $fileContent.find('textarea');
            
            if ($textarea.length > 0) {
                const scrollState = window.tabScrollStates[targetTabId];
                if (scrollState) {
                    // 重置滚动状态，允许自动滚动
                    scrollState.isUserScrolling = false;
                    scrollState.isAtBottom = true;
                }
                
            }
        }, 3000);
    });
    
});

// 启动自动刷新
function startAutoRefresh() {
    if (window.autoRefreshInterval) {
        clearInterval(window.autoRefreshInterval);
    }
    
    window.autoRefreshInterval = setInterval(function() {
        if (window.autoRefreshEnabled) {
            const activeTabInfo = getCurrentActiveTab();
            
            if (activeTabInfo.exists && activeTabInfo.fileName) {
                refreshCurrentTabIncremental(); // 增量刷新
            }
        }
    }, 3000);
    
}

// 停止自动刷新
function stopAutoRefresh() {
    if (window.autoRefreshInterval) {
        clearInterval(window.autoRefreshInterval);
        window.autoRefreshInterval = null;
    }
}

// 初始化自动刷新按钮状态
function initializeAutoRefreshButton() {
    const $btn = $('#autoRefreshBtn');
    
    if (window.autoRefreshEnabled) {
        // 启用状态
        $btn.removeClass('btn-outline-warning').addClass('btn-outline-success');
        $btn.attr('title', '停止自动刷新 (3秒)');
        $btn.html('停止刷新');
    } else {
        // 停止状态
        $btn.removeClass('btn-outline-success').addClass('btn-outline-warning');
        $btn.attr('title', '开始自动刷新 (3秒)');
        $btn.html('开始刷新');
    }
}

// 初始化最大行数下拉框
function initializeMaxLines() {
    const $select = $('#maxLines');
    $select.val(window.maxLines);
}

// 更新最大行数
function updateMaxLines(newMaxLines) {
    window.maxLines = newMaxLines;
    
    // 保存到本地存储
    localStorage.setItem('maxLines', newMaxLines.toString());
    
    // 重新加载当前活动tab的日志内容
    const activeTab = $('.tab-pane.active');
    if (activeTab.length > 0) {
        const fileName = activeTab.data('fileName');
        const tabId = activeTab.attr('id');
        
        if (fileName) {
            // 显示加载状态
            const $fileContent = activeTab.find('.file-content');
            $fileContent.html(`
                <div class="text-center text-muted h-100 d-flex flex-column justify-content-center">
                    <div class="loading"></div>
                    <p class="mt-3">正在重新加载日志内容...</p>
                </div>
            `);
            
            // 重新加载tab内容
            loadTabContent(tabId, fileName);
        }
    }
    
    // 显示通知
    showNotification(`最大显示行数已更改为 ${newMaxLines} 行，正在重新加载日志`, 'info');
}

// 切换自动刷新状态
function toggleAutoRefresh() {
    window.autoRefreshEnabled = !window.autoRefreshEnabled;
    
    // 保存设置到本地存储
    localStorage.setItem('autoRefreshEnabled', window.autoRefreshEnabled.toString());
    
    const $btn = $('#autoRefreshBtn');
    
    if (window.autoRefreshEnabled) {
        // 启用自动刷新
        $btn.removeClass('btn-outline-warning').addClass('btn-outline-success');
        $btn.html('停止刷新');
        
        // 重新启动定时器
        startAutoRefresh();
        
        showNotification('自动刷新已启用 (3秒)', 'success');
    } else {
        // 停止自动刷新
        $btn.removeClass('btn-outline-success').addClass('btn-outline-warning');
        $btn.html('开始刷新');
        
        // 停止定时器
        stopAutoRefresh();
        
        showNotification('自动刷新已停止', 'warning');
    }
    
}

// 加载日志文件列表
function loadLogFiles() {
    $.ajax({
        url: '/deploy/logs/file/log-files',
        method: 'GET',
        dataType: 'json',
        success: function(response) {
            if (response.success) {
                populateLogFileSelect(response.logFiles);
            } else {
                showNotification('加载日志文件列表失败: ' + response.message, 'error');
            }
        },
        error: function(xhr, status, error) {
            showNotification('请求日志文件列表失败: ' + error, 'error');
        }
    });
}

// 填充日志文件选择下拉框
function populateLogFileSelect(logFiles) {
    const $select = $('#fileSelect');
    
    // 清空现有选项（保留第一个默认选项）
    $select.find('option:not(:first)').remove();
    
    // 直接添加日志文件选项（后端已经完成过滤和排序）
    logFiles.forEach(function(fileInfo) {
        const option = $('<option></option>')
            .attr('value', fileInfo.fileName)
            .text(fileInfo.displayName);
        $select.append(option);
    });
    
    // 触发 Select2 更新以刷新选项
    $select.trigger('change.select2');
}


// 从文件名中提取应用名称（不包含版本号）
function extractAppNameFromFileName(fileName) {
    // 移除.log后缀
    const nameWithoutExt = fileName.replace(/\.log$/i, '');
    
    // 尝试匹配格式：appCode-version_timestamp
    // 例如：medical-platform-1.0.0_20241010_123456.log
    const pattern1 = /^(.+)-(\d+\.\d+\.\d+)_(\d{8}_\d{6})$/;
    let match = nameWithoutExt.match(pattern1);
    
    if (match) {
        return match[1]; // 返回应用名（不包含版本号）
    }
    
    // 尝试匹配格式：appCode-version_timestamp（其他版本格式）
    // 例如：build_medical-platform_v2025.87_20251009_111243.log
    const pattern2 = /^(.+)-([^_]+)_(\d{8}_\d{6})$/;
    match = nameWithoutExt.match(pattern2);
    
    if (match) {
        return match[1]; // 返回应用名（不包含版本号）
    }
    
    // 尝试匹配格式：appCode_timestamp（无版本号）
    // 例如：pms-admin_20241010_123456.log
    const pattern3 = /^(.+)_(\d{8}_\d{6})$/;
    match = nameWithoutExt.match(pattern3);
    
    if (match) {
        return match[1]; // 返回应用名
    }
    
    // 如果都不匹配，返回整个文件名（去掉.log）
    return nameWithoutExt;
}

// 显示空状态
function showEmptyState() {
    $('#tabContainer').hide();
    $('#defaultContent').show();
}

// 创建或切换到指定文件的tab
function createOrSwitchToTab(fileName) {
    const tabId = 'tab-' + fileName.replace(/[^a-zA-Z0-9]/g, '-');
    const tabLabel = extractAppNameFromFileName(fileName);
    
    // 检查tab是否已存在
    if ($('#' + tabId).length === 0) {
        createTab(tabId, tabLabel, fileName);
    }
    
    // 切换到该tab
    switchToTab(tabId);
    
    // 显示tab容器，隐藏默认内容
    $('#tabContainer').show();
    $('#defaultContent').hide();
}

// 创建新的tab
function createTab(tabId, tabLabel, fileName) {
                // 创建tab标签
                const tabNav = $(`
                    <li class="nav-item" role="presentation">
                        <button class="nav-link" id="${tabId}-tab" data-bs-toggle="tab"
                                data-bs-target="#${tabId}" type="button" role="tab"
                                aria-controls="${tabId}" aria-selected="false">
                            ${tabLabel}
                        </button>
                    </li>
                `);
    
    // 创建tab内容
    const tabContent = $(`
        <div class="tab-pane fade" id="${tabId}" role="tabpanel" 
             aria-labelledby="${tabId}-tab">
            <div class="file-content">
                <div class="text-center text-muted h-100 d-flex flex-column justify-content-center">
                    <div class="loading"></div>
                    <p class="mt-3">正在加载日志内容...</p>
                </div>
            </div>
        </div>
    `);
    
    // 添加到页面
    $('#logTabs').append(tabNav);
    $('#logTabContent').append(tabContent);
    
    // 存储文件名到tab元素
    $('#' + tabId).data('fileName', fileName);
    
    // 初始化滚动状态
    window.tabScrollStates[tabId] = {
        isUserScrolling: false,
        isAtBottom: true,
        lastScrollTop: 0
    };
    
    // 添加滚动事件监听器
    setupScrollListener(tabId);
    
    // 加载文件内容
    loadTabContent(tabId, fileName);
}

// 切换到指定tab
function switchToTab(tabId) {
    // 激活tab标签
    $('#' + tabId + '-tab').tab('show');
    
    // 更新滚动状态，确保新激活的tab能正确滚动
    setTimeout(function() {
        const $fileContent = $('#' + tabId + ' .file-content');
        const $textarea = $fileContent.find('textarea');
        
        if ($textarea.length > 0) {
            const scrollState = window.tabScrollStates[tabId];
            if (scrollState) {
                // 重置滚动状态，允许自动滚动
                scrollState.isUserScrolling = false;
                scrollState.isAtBottom = true;
            }
            
        }
    }, 3000);
}

// 设置滚动监听器
function setupScrollListener(tabId) {
    const $fileContent = $('#' + tabId + ' .file-content');
    const $textarea = $fileContent.find('textarea');
    
    // 监听滚动事件
    $textarea.on('scroll', function() {
        const scrollState = window.tabScrollStates[tabId];
        const scrollTop = this.scrollTop;
        const scrollHeight = this.scrollHeight;
        const clientHeight = this.clientHeight;
        
        // 判断是否在底部（允许5px的误差）
        const isAtBottom = (scrollTop + clientHeight >= scrollHeight - 5);
        
        // 判断用户是否在向上滚动
        const isScrollingUp = scrollTop < scrollState.lastScrollTop;
        
        // 更新滚动状态
        scrollState.isAtBottom = isAtBottom;
        
        // 更智能的用户滚动检测：
        // 1. 如果用户在底部附近（距离底部小于50px），不算作用户滚动
        // 2. 只有当用户明显向上滚动且不在底部附近时，才算作用户滚动
        const distanceFromBottom = scrollHeight - (scrollTop + clientHeight);
        const isNearBottom = distanceFromBottom < 50;
        
        scrollState.isUserScrolling = !isAtBottom && !isNearBottom && isScrollingUp;
        scrollState.lastScrollTop = scrollTop;
        
        // 如果用户滚动到接近底部，重置用户滚动状态
        if (isAtBottom || isNearBottom) {
            scrollState.isUserScrolling = false;
        }
        
        // 更新滚动到底部按钮的状态
        updateScrollToBottomButtonState(tabId, isAtBottom);
    });
}

// 更新滚动到底部按钮状态
function updateScrollToBottomButtonState(tabId, isAtBottom) {
    const $btn = $('#scrollToBottomBtn');
    const $icon = $btn.find('i');
    
    if (isAtBottom) {
        $btn.removeClass('btn-outline-info').addClass('btn-outline-success');
        $icon.removeClass('fa-arrow-down').addClass('fa-check');
        $btn.attr('title', '已到底部');
    } else {
        $btn.removeClass('btn-outline-success').addClass('btn-outline-info');
        $icon.removeClass('fa-check').addClass('fa-arrow-down');
        $btn.attr('title', '滚动到底部');
    }
}

// 加载tab内容
function loadTabContent(tabId, fileName) {
    const $tabContent = $('#' + tabId + ' .file-content');
    
    // 首次加载时读取最后maxLines行，使用新的文件API
    $.ajax({
        url: '/deploy/logs/file/read-file-last-lines',
        method: 'GET',
        data: { 
            fileName: fileName,
            lastLines: window.maxLines || 2000
        },
        dataType: 'json',
        success: function(response) {
            if (response.success) {
                displayFileContent($tabContent, response.content, fileName);
                // 初始化行数计数为总行数，用于后续增量查询
                window.tabLineCounts[tabId] = response.totalLines;
                
                // 显示提示信息
                if (response.totalLines > response.actualLines) {
                    showNotification(`显示最后 ${response.actualLines} 行，共 ${response.totalLines} 行`, 'info');
                }
            } else {
                $tabContent.html(`
                    <div class="alert alert-danger">
                        <h6>读取失败：</h6>
                        <p>${response.message}</p>
                        ${response.configuredDirectory ? `<p><small>文件目录: ${response.configuredDirectory}</small></p>` : ''}
                    </div>
                `);
            }
        },
        error: function(xhr, status, error) {
            $tabContent.html(`
                <div class="alert alert-danger">
                    <h6>请求失败：</h6>
                    <p>状态码：${xhr.status}</p>
                    <p>错误信息：${error}</p>
                </div>
            `);
        }
    });
}

// 增量加载tab内容（只对当前活动tab生效）
function loadTabContentIncremental(tabId, fileName) {
    const $tabContent = $('#' + tabId + ' .file-content');
    
    // 检查是否为当前活动tab
    if (!isCurrentActiveTab(tabId)) {
        return;
    }
    
    // 获取当前已读取的行数
    const currentLineCount = window.tabLineCounts[tabId] || 0;
    
    // 使用新的增量API进行增量读取
    $.ajax({
        url: '/deploy/logs/file/read-file-incremental',
        method: 'GET',
        data: { 
            fileName: fileName,
            fromLine: currentLineCount
        },
        dataType: 'json',
        success: function(response) {
            if (response.success && response.hasNewContent) {
                // 有新内容，追加到现有内容
                appendNewContentOnly($tabContent, response.content, fileName);
                
                // 更新行数计数
                window.tabLineCounts[tabId] = response.totalLines;

            }
        },
        error: function(xhr, status, error) {
            // 静默处理错误
        }
    });
}


// 增量刷新当前活动tab
function refreshCurrentTabIncremental() {
    const activeTab = $('.tab-pane.active');
    if (activeTab.length > 0) {
        const fileName = activeTab.data('fileName');
        const tabId = activeTab.attr('id');
        
        if (fileName) {
            loadTabContentIncremental(tabId, fileName);
        }
    }
}

// 显示文件内容
function displayFileContent($container, content, fileName) {
    // 所有文件类型都直接显示原始内容，不进行任何转换
    $container.html(`<textarea class="form-control" tabindex="0">${content}</textarea>`);
}



// 立即滚动到底部
function scrollToBottomImmediately($container) {
    const $textarea = $container.find('textarea');
    if ($textarea.length > 0) {
        requestAnimationFrame(function() {
            const scrollHeight = $textarea[0].scrollHeight;
            const clientHeight = $textarea[0].clientHeight;
            const maxScrollTop = scrollHeight - clientHeight;
            
            $textarea[0].scrollTop = maxScrollTop;
            
            const tabId = $container.closest('.tab-pane').attr('id');
            const scrollState = window.tabScrollStates[tabId];
            if (scrollState) {
                scrollState.isAtBottom = true;
                scrollState.isUserScrolling = false;
                scrollState.lastScrollTop = maxScrollTop;
            }
        });
    }
}


// 只追加新内容，不重置整个内容
function appendNewContentOnly($container, newContent, fileName) {
    if (!newContent || newContent.trim() === '') {
        return;
    }
    
    let $textarea = $container.find('textarea');
    if ($textarea.length === 0) {
        // 如果没有textarea元素，创建新的
        // 直接显示原始内容，不进行任何转换
        $container.html(`<textarea class="form-control" tabindex="0">${newContent}</textarea>`);
        
        // 新内容，滚动到底部
        setTimeout(function() {
            scrollToBottomImmediately($container);
        }, 1000);
    } else {
        // 追加到现有的textarea元素
        const existingContent = $textarea.val();
        let newFormattedContent;
        
        // 直接使用原始内容，不进行任何转换
        newFormattedContent = newContent;
        
        // 直接追加，不重新设置整个内容
        $textarea.val(existingContent + newFormattedContent);
        
        
        // 自动滚动到底部
        setTimeout(function() {
            scrollToBottomImmediately($container);
        }, 1000);
        
    }
}



// 下载日志（使用文件名）
function downloadLogFile(fileName) {
    window.open('/deploy/logs/file/download-file?fileName=' + encodeURIComponent(fileName), '_blank');
}

// 强制滚动到底部（最简单直接的方法）
function forceScrollToBottomSimple() {
    const activeTab = $('.tab-pane.active');
    if (activeTab.length > 0) {
        const $fileContent = activeTab.find('.file-content');
        const $textarea = $fileContent.find('textarea');
        
        if ($textarea.length > 0) {
            $textarea[0].scrollTop = $textarea[0].scrollHeight;
        }
    }
}

// 工具函数：显示通知
function showNotification(message, type = 'info') {
    const alertClass = type === 'success' ? 'alert-success' : 
                      type === 'error' ? 'alert-danger' : 
                      type === 'warning' ? 'alert-warning' : 'alert-info';
    
    const notification = $(`
        <div class="alert ${alertClass} alert-dismissible fade show position-fixed" 
             style="top: 20px; right: 20px; z-index: 9999; min-width: 300px;">
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        </div>
    `);
    
    $('body').append(notification);
    
    // 3秒后自动消失
    setTimeout(function() {
        notification.alert('close');
    }, 3000);

}

// 获取当前活动tab信息
function getCurrentActiveTab() {
    const activeTab = $('.tab-pane.active');
    if (activeTab.length > 0) {
        return {
            tabId: activeTab.attr('id'),
            fileName: activeTab.data('fileName'),
            exists: true
        };
    }
    return { exists: false };
}

// 检查tab是否为当前活动tab
function isCurrentActiveTab(tabId) {
    const activeTab = $('.tab-pane.active');
    return activeTab.length > 0 && activeTab.attr('id') === tabId;
}