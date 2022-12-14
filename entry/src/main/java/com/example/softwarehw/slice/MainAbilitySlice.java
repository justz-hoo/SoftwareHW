package com.example.softwarehw.slice;
import com.example.softwarehw.MyApplication;
import com.example.softwarehw.ResourceTable;
import com.example.softwarehw.bean.ChatDataBean;
import com.example.softwarehw.provider.ChatProvider;

import com.example.softwarehw.util.SensitiveWordFilter;
import com.example.softwarehw.util.Tools;

import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.aafwk.content.IntentParams;
import ohos.aafwk.content.Operation;
import ohos.agp.components.Button;
import ohos.agp.components.Component;
import ohos.agp.components.ListContainer;
import ohos.agp.components.TextField;
import ohos.agp.window.dialog.ToastDialog;
import ohos.app.Context;
import ohos.bundle.IBundleManager;
import ohos.data.distributed.common.*;
import ohos.data.distributed.user.SingleKvStore;
import ohos.global.resource.NotExistException;
import ohos.global.resource.Resource;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import ohos.utils.zson.ZSONArray;
import ohos.utils.zson.ZSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static ohos.security.SystemPermission.DISTRIBUTED_DATASYNC;

public class MainAbilitySlice extends AbilitySlice {
    private Context mContext;
    // 聊天列表
    private ListContainer lcList;
    // 聊天数据
    private final List<ChatDataBean> listData = new ArrayList<>();
    // 聊天数据适配器
    private ChatProvider chatProvider;
    // 输入框
    private TextField tfContent;
    // 发送按钮
    private Button btnSend;
    // 更多按钮
    private  Button btnMore;
    // 跳转历史记录
    private Button btnHirtory;

    // 分布式数据库
    private SingleKvStore KvStore;
    // 存入的列表数据key
    private static final String KEY_DATA = "chat_data";
    // 存入的头像索引
    private static final String KEY_PIC_INDEX = "key_pic_index";
    private int picIndex = 0;

    private String user_id = "";
    static final HiLogLabel label = new HiLogLabel(HiLog.LOG_APP, 0x12345, "MainAbilitySlice");

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        get_info(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);
        mContext = this;
        initComponent();
        initDatabase();
    }
    void get_info(Intent intent) //  从login页面获取传参：用户id
    {
        Operation operation = intent.getOperation();
        IntentParams intentParams = intent.getParams();
        user_id = (String)intentParams.getParam("user_id");
        new ToastDialog(this).setText(user_id).show();
    }

    /**
     * 初始化组建
     */
    private void initComponent() {
        lcList = (ListContainer) findComponentById(ResourceTable.Id_lc_list);
        tfContent = (TextField) findComponentById(ResourceTable.Id_tf_content);
        tfContent.setAdjustInputPanel(true);
        btnSend = (Button) findComponentById(ResourceTable.Id_btn_send);
        btnSend.setEnabled(false);
        btnMore = (Button) findComponentById(ResourceTable.Id_btn_more);
        btnMore.setEnabled(true);
        btnHirtory= (Button) findComponentById(ResourceTable.Id_btn_history);
        btnHirtory.setEnabled(true);
        btnHirtory.setClickedListener(this::onClick);
        // 初始化适配器
        chatProvider = new ChatProvider(mContext, listData);
        lcList.setItemProvider(chatProvider);

        // 输入框内容变化监听
        tfContent.addTextObserver((text, start, before, count) -> {
            btnSend.setEnabled(text.length() != 0);
        });

        // @TODO:模拟一下sender, receiver, 要从数据库中读取
        String sender = "admin".equals(user_id)? "piggy":"krasus";
        String receiver = "admin".equals(user_id)? "krasus":"piggy";
        // 点击发送按钮
        btnSend.setClickedListener(component -> {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.now();
            String content = tfContent.getText().trim();
            String content_final = null;

            // @TODO:进行关键词剔除算法
            try {
                String encoding = "UTF-8";
                Resource resource = this.getResourceManager().getResource(ResourceTable.Profile_SensitiveWordList);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource, encoding));
                String line;
                ArrayList<String> list = new ArrayList<>(); // 用于拼接读取到的数据
                while ((line = bufferedReader.readLine()) != null){
                    list.add(line);
                }
                // 释放资源
                bufferedReader.close();
                SensitiveWordFilter.loadWord(list);
                content_final = SensitiveWordFilter.Filter(content);
                if (content_final == null) content_final = content;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NotExistException e) {
                e.printStackTrace();
            }
            // 更新listData
            listData.add(new ChatDataBean(Tools.getDeviceId(mContext),
                    sender, receiver, date+"", time+"", picIndex, content_final));
            HiLog.info(label, "listData:ok!");
            // 存入数据库中
            KvStore.putString(KEY_DATA, ZSONObject.toZSONString(listData));
            HiLog.info(label, "listData:ok?"+ ZSONObject.toZSONString(listData));
            // 清空输入框
            tfContent.setText("");
        });

        // 点击更多按钮
        btnMore.setClickedListener(this::onClickMore);
    }
    public void onClick(Component component)
    {
        if(component == btnHirtory)
        {
            gotoHistory();
        }
    }

    // 跳转函数
    public void gotoHistory()
    {
        Intent intent = new Intent();
        String listData_str = "";
        IntentParams intentParams = new IntentParams();
        listData_str = ZSONObject.toZSONString(listData);
        intentParams.setParam("listData",listData_str);
        intent.setParams(intentParams);

        present(new HistoryAbilitySlice(),intent);
    }

    // @TODO: 跳出更多界面
    private void onClickMore(Component component) {
        if (component == (Component) btnMore) {
            System.out.println("");
        }
    }

    /**
     * 从MyApplication获取分布式数据库
     */
    private void initDatabase()
    {
        KvStore = MyApplication.getInstance().getKvStore();
        // 监听数据库数据改变
        KvStore.subscribe(SubscribeType.SUBSCRIBE_TYPE_ALL, new KvStoreObserver()
        {
            @Override
            public void onChange(ChangeNotification changeNotification) {
                KvStoreObserver.super.onChange(changeNotification);
                List<Entry> insertEntries = changeNotification.getInsertEntries();
                List<Entry> updateEntries = changeNotification.getUpdateEntries();

                // 第一次存入数据， 获取insertEntries
                if (insertEntries.size() > 0) {
                    for (Entry entry : insertEntries) {  // 遍历insertEntries
                        if (KEY_DATA.equals(entry.getKey())) {  // 找到entry中key相同的
                            // 回调为非UI线程， 需要在UI更新线程
                            getUITaskDispatcher().syncDispatch(() -> {
                                listData.clear();
                                listData.addAll(ZSONArray.stringToClassList(entry.getValue().getString(), ChatDataBean.class));
                                chatProvider.notifyDataChanged();
                                lcList.scrollTo(listData.size() -1 );
                            });
                        }
                    }
                }
                else if (updateEntries.size() > 0){
                    for (Entry entry : updateEntries) {
                        if (KEY_DATA.equals(entry.getKey())) {
                            getUITaskDispatcher().syncDispatch(() -> {
                                listData.clear();
                                listData.addAll(ZSONArray.stringToClassList(entry.getValue().getString(), ChatDataBean.class));
                                chatProvider.notifyDataChanged();
                                lcList.scrollTo(listData.size()-1);
                            });
                        }
                    }
                }
            }
        });

        try {
            picIndex = KvStore.getInt(KEY_PIC_INDEX);  // 找到数据库中“key_pic_index”的queries的int值
            KvStore.putInt(KEY_PIC_INDEX, picIndex + 1);
        }
        catch (KvStoreException e) {
            e.printStackTrace();

            // 没有找到，首次进入
            if (e.getKvStoreErrorCode() == KvStoreErrorCode.KEY_NOT_FOUND) {
                picIndex = 0;
                KvStore.putInt(KEY_PIC_INDEX, picIndex + 1);
            }
        }
    }


    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
