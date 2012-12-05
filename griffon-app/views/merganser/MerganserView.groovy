package merganser

import ca.odell.glazedlists.*
import ca.odell.glazedlists.gui.*
import ca.odell.glazedlists.swing.*
import java.awt.*
import java.awt.dnd.*
import java.awt.datatransfer.*
import java.text.DecimalFormat
import javax.swing.*
import javax.swing.filechooser.*
import net.miginfocom.swing.*
import javax.swing.ListCellRenderer

def confirmAndExecute( frame, message, initFocus, action ) {
    { evt ->
        if( JOptionPane.showOptionDialog(
                frame,
                message,
                "確認",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                [
                    "はい",
                    "いいえ"
                ] as String[],
                initFocus,
            ) == JOptionPane.YES_OPTION ) {
            action(evt)
        }
    }

}

final Color FOCUS_COLOR = new Color(200, 255, 200)

def createTableModel(eventList, Map columns) {
    new EventTableModel(
        eventList,
        [
            getColumnCount: { columns*.key.size() },
            getColumnName: { index -> columns*.key[index] },
            getColumnValue: { object, index -> columns*.value[index](object) },
        ] as TableFormat
    )
}

def createComboBoxModel(eventList) {
    new EventComboBoxModel(
        eventList
    )
}

def showContextMenu = { e ->
    if( SwingUtilities.isRightMouseButton(e) ) {
        view.editPopup.parent = e.source
        view.editPopup.show(e.source, e.x, e.y)
    }
}


application(
    id:"mainFrame",
    title:"${model.appName}",
    pack: true,
    show:true,
    defaultCloseOperation:JFrame.DO_NOTHING_ON_CLOSE,
    preferredSize:[700,760],
    windowClosing:controller.windowClosing,
    locationByPlatform:true,
    iconImage: imageIcon('/griffon-icon-48x48.png').image,
    iconImages: [
        imageIcon('/griffon-icon-48x48.png').image,
        imageIcon('/griffon-icon-32x32.png').image,
        imageIcon('/griffon-icon-16x16.png').image
    ],
    enabled:bind( source:model, sourceProperty:"editable" ),
    visible:false,
) {

    layout:migLayout(
        layoutConstraints:"fill",
        columnConstraints:"[grow]",
    )

    menuBar(
        constraints:"north",
    ) {
    }

    scrollPane(
        constraints:"center, grow",
    ) {
        panel(){
            migLayout(
                layoutConstraints:"fillx",
                columnConstraints:"[grow]",
                rowConstraints:"[][][][::110]",
            )

            panel(
                constraints:"growx, wrap",
            ) {

                migLayout(
                    layoutConstraints:"fillx",
                )

                panel(
                    constraints:"growx, wrap",
                ) {
                    migLayout(
                        layoutConstraints:"fillx",
                        columnConstraints:"[][][]",
                    )

                    comboBox(
                        model:createComboBoxModel(
                            model.twitterAccountForComboBoxEventList,
                        ),
                        selectedItem:bind( source:model, sourceProperty:"selectedTwitterAccount", mutual:true ),
                        renderer:{ list, twitterAccount, index, isSelected, isFocused ->
                            if( !twitterAccount )
                                return

                            label(
                                text:"<html>${twitterAccount.screenName}" + 
                                    (twitterAccount.nickName ? "(${twitterAccount.nickName})" : "") +
                                    "<br>follower:${twitterAccount.totalFollowCount}",
                                icon:imageIcon(
                                    url:twitterAccount?.profileImageUrl ? new URL(twitterAccount?.profileImageUrl) : null
                                )
                            )
                        } as ListCellRenderer,
                        enabled:bind{
                            !model.followingOrEditing
                        },
                        constraints:"growx",
                        doubleBuffered:true,
                    )

                    button(
                        text:"設定を編集",
                        actionPerformed:confirmAndExecute(
                            view.mainFrame,
                            "選択されたアカウントの設定を編集します。\nよろしいですか？",
                            "はい",
                            controller.selectTwitterAccount,
                        ),
                        constraints:"",
                        enabled:bind( source:model, sourceProperty:"editButtonEnabled" ),
                        font:new Font("monospace", Font.PLAIN, 15),
                    )

                    button(
                        text:"アカウントを追加",
                        actionPerformed:confirmAndExecute(
                            view.mainFrame,
                            "アカウントを追加します。\nよろしいですか？",
                            "はい",
                            controller.insertTwitterAccount,
                        ),
                        constraints:"",
                        enabled:bind{
                            !model.followingOrEditing
                        },
                        font:new Font("monospace", Font.PLAIN, 15),
                        icon:silkIcon("add"),
                    )

                }

                panel(
                    border:titledBorder(
                        title:"自動フォロー機能　設定",
                    ),
                    constraints:"growx, wrap",
                ) {
                    migLayout(
                        columnConstraints:"[150][grow]",
                    )

                    panel()

                    checkBox(
                        "有効にする",
                        selected:bind( source:model, sourceProperty:"twitterAccountDoFollow", mutual:true ),
                        constraints:"wrap",
                        enabled:bind( source:model, sourceProperty:"editing" ),
                    )

                    label(
                        text:"ツイートの検索キーワード" ,
                        foreground:new Color(32, 57, 168),
                    )

                    textField(
                        text:bind( source:model, sourceProperty:"twitterAccountSearchTweetKeywords", mutual:true ),
                        constraints:"growx, split 3",
                        dragEnabled:true,
                        mousePressed:showContextMenu,
                        enabled:bind( source:model, sourceProperty:"editing" ),
                    )
                    textField(
                        text:bind( source:model, sourceProperty:"twitterAccountSearchTweetKeywords2", mutual:true ),
                        constraints:"growx",
                        dragEnabled:true,
                        mousePressed:showContextMenu,
                        enabled:bind( source:model, sourceProperty:"editing" ),
                    )
                    textField(
                        text:bind( source:model, sourceProperty:"twitterAccountSearchTweetKeywords3", mutual:true ),
                        constraints:"growx, wrap",
                        dragEnabled:true,
                        mousePressed:showContextMenu,
                        enabled:bind( source:model, sourceProperty:"editing" ),
                    )

                }

                panel(
                    border:titledBorder(
                        title:"自動リフォロー機能　設定",
                    ),
                    constraints:"growx, wrap",
                ) {
                    migLayout(
                        columnConstraints:"[150][grow]",
                    )

                    panel()

                    checkBox(
                        "有効にする",
                        selected:bind( source:model, sourceProperty:"twitterAccountDoRefollow", mutual:true ),
                        enabled:bind( source:model, sourceProperty:"editing" ),
                    )
                }

                panel(
                    constraints:"wrap",
                ) {
                    migLayout(
                        layoutConstraints:"insets 5 19 5 5",
                        columnConstraints:"[150][grow]",
                    )

                    label(
                        text:"一日当たりのフォロー数上限" ,
                        foreground:new Color(32, 57, 168),
                    )

                    textField(
                        text:bind( source:model, sourceProperty:"twitterAccountMaxFollowCountForOneDay", mutual:true ),
                        dragEnabled:true,
                        mousePressed:showContextMenu,
                        columns:2,
                        horizontalAlignment:JTextField.RIGHT,
                        enabled:bind( source:model, sourceProperty:"editing" ),
                        constraints:"split 2",
                    )
                    
                    label(
                        text:"人" ,
                        constraints:"wrap",
                    )

                    panel()

                    checkBox(
                        "重複可(一度解除したフォロアーも再フォローする)",
                        selected:bind( source:model, sourceProperty:"twitterAccountDoRefollowInBlackList", mutual:true ),
                        constraints:"span 6, wrap",
                        enabled:bind( source:model, sourceProperty:"editing" ),
                    )

                }

                panel(
                    border:titledBorder(
                        title:"全アカウント共通　設定",
                    ),
                    constraints:"growx, wrap",
                ) {
                    migLayout(
                        columnConstraints:"[150][grow]",
                    )

                    label(
                        text:"自動フォロー実行間隔" ,
                        foreground:new Color(32, 57, 168),
                    )

                    textField(
                        text:bind( source:model, sourceProperty:"waitingSeconds", mutual:true ),
                        dragEnabled:true,
                        mousePressed:showContextMenu,
                        columns:3,
                        horizontalAlignment:JTextField.RIGHT,
                        enabled:bind( source:model, sourceProperty:"editing" ),
                        constraints:"split 6",
                    )

                    label(
                        text:"秒" ,
                    )

                    label(
                        text:"+" ,
                    )

                    label(
                        text:"揺らぎ間隔" ,
                    )

                    textField(
                        text:bind( source:model, sourceProperty:"randomWaitingSeconds", mutual:true ),
                        dragEnabled:true,
                        mousePressed:showContextMenu,
                        horizontalAlignment:JTextField.RIGHT,
                        enabled:bind( source:model, sourceProperty:"editing" ),
                        constraints:"split 2",
                    )

                    label(
                        text:"秒(60秒推奨)" ,
                        constraints:"wrap",
                    )

                }

                panel(
                    constraints:"wrap",
                ) {

                    migLayout(
                        columnConstraints:"[grow]",
                    )

                    button(
                        text:"保存",
                        actionPerformed:confirmAndExecute(
                            view.mainFrame,
                            "設定を保存します。\nよろしいですか？",
                            "はい",
                            controller.updateTwitterAccount,
                        ),
                        enabled:bind( source:model, sourceProperty:"editing" ),
                        constraints:"split 4, align right",
                        icon:silkIcon("disk"),
                    )
                    button(
                        text:"キャンセル",
                        actionPerformed:confirmAndExecute(
                            view.mainFrame,
                            "入力された内容をキャンセルします。\nよろしいですか？",
                            "いいえ",
                            controller.cancelTwitterAccount,
                        ),
                        background:new Color(252, 251, 184),
                        enabled:bind( source:model, sourceProperty:"editing" ),
                        icon:silkIcon("cancel"),
                    )
                    button(
                        text:"再フォロー禁止リスト参照",
                        actionPerformed:controller.showTwitterAccountsInBlackList,
                        // TODO
        //                background:new Color(252, 184, 200),
                        enabled:bind{
                            model.editing && !!model.selectedTwitterAccount
                        },
                    )
                    button(
                        text:"再フォロー禁止リスト削除",
                        actionPerformed:controller.deleteTwitterAccountsInBlackList,
                        background:new Color(252, 184, 200),
                        enabled:bind{
                            model.editing && !!model.selectedTwitterAccount
                        },
                    )
                    button(
                        text:"削除",
                        actionPerformed:controller.deleteTwitterAccount,
                        background:new Color(252, 184, 200),
                        enabled:bind{
                            model.editing && !!model.selectedTwitterAccount
                        },
                        icon:silkIcon("delete"),
                    )
                }
            }

            label(
                text:"↓ ↓ ↓" ,
                constraints:"align center, gapright 30%, gapleft 30%, wrap",
                foreground:new Color(32, 57, 168),
            )

            panel(
                constraints:"growx, align center, gapright 20%, gapleft 20%, wrap",
            ) {
                button(
                    text:"自動フォロー開始",
                    actionPerformed:confirmAndExecute(
                        view.mainFrame,
                        "設定をもとに自動フォローを開始しますか？",
                        "いいえ",
                        controller.startFollow,
                    ),
                    background:bind{ model.following ? new Color(156, 224, 144) : new Color(224, 148, 144) },
                    visible:bind{!model.following},
                    enabled:bind{!model.selectedTwitterAccount},
                    icon:silkIcon("server_go"),
                )

                button(
                    text:"自動フォロー終了",
                    actionPerformed:confirmAndExecute(
                        view.mainFrame,
                        "自動フォローを終了しますか？",
                        "いいえ",
                        controller.endFollow,
                    ),
                    visible:bind{model.following},
                    background:new Color(252, 251, 184),
                    constraints:"growx, align center, gapright 20%, gapleft 20%, wrap",
                    icon:silkIcon("stop"),
                )
            }

            scrollPane(
                constraints:"grow",
            ) {
                table(
                    selectionMode:ListSelectionModel.SINGLE_SELECTION,
                    autoCreateRowSorter:true,
                    model:createTableModel(
                        model.twitterAccountEventList,
                        [
                            "ユーザID":{ it.screenName + (it.nickName ? "(${it.nickName})" : "") },
                            "全フォロー数":{ it.totalFollowCount },
                            "ツールからのﾌｫﾛｰ数":{ it.merganserFollowingCount },
                            "今回フォロー数":{ it.lastFollowCount},
                            "本日フォロー数":{ it.todayFollowCount},
                        ]
                    ),
                ) {
                    current.tableHeader.reorderingAllowed = false
                }

            }
        }

    }

    textField(
        constraints:"south",
        text:bind( source:model, sourceProperty:"statusbarText" ),
        enabled:false,
        disabledTextColor:Color.RED,
        background:Color.GRAY,
        font:new Font("monospace", Font.BOLD, 13),
    )

}


popupMenu(
    id:"editPopup",
) {
    menuItem("切り取り(T)", actionPerformed:{ e ->
        e.source.parent.parent.cut()
    })
    menuItem("コピー(C)", actionPerformed:{ e ->
        e.source.parent.parent.copy()
    })
    menuItem("貼り付け(P)", actionPerformed:{ e ->
        e.source.parent.parent.paste()
    })
    menuItem("削除(D)", actionPerformed:{ e ->
        def tf = e.source.parent.parent
        def text = ""

        if ( tf.selectionStart != 0 )
            text += tf.text[0..( tf.selectionStart-1 )]
        if ( tf.selectionEnd != tf.text.size() )
            text += tf.text[( tf.selectionEnd )..-1]
         tf.text = text
    })
}

view.editPopup.metaClass.parent = null

dialog(
    id:"blackListDialog",
    title:"${model.appName} - 再フォロー禁止リスト",
    defaultCloseOperation:JFrame.HIDE_ON_CLOSE,
    modal:true,
    visible:false,
    pack: true,
    preferredSize:[300,500],
    owner:view.mainFrame,
) {
    migLayout(
        layoutConstraints:"fill",
        columnConstraints:"[grow]",
        rowConstraints:"[grow]",
    )

    scrollPane(
        constraints:"growx, wrap",
    ) {
        table(
            selectionMode:ListSelectionModel.SINGLE_SELECTION,
            autoCreateRowSorter:true,
            model:createTableModel(
                model.twitterAccountInBlackListEventList,
                [
                    "アカウント名":{ it.nickName }
                ]
            ),
        ) {
            current.tableHeader.reorderingAllowed = false
        }
    }

}

dialog(
    id:"apiKeyInputDialog",
    title:"${model.appName} - APIキー入力",
    defaultCloseOperation:WindowConstants.DISPOSE_ON_CLOSE,
    modal:true,
    resizable:false,
    pack: true,
    preferredSize:[400,130],
    owner:view.mainFrame,
    windowClosing:{ evt ->
        model.consumerKey = ""
        model.consumerSecret = ""
        this.dispose()
    }
) {
    migLayout(
        layoutConstraints:"fill",
        columnConstraints:"[grow][grow][grow]",
        rowConstraints:"[][grow]",
    )

    label(
        text:"コンシューマキー" ,
    )
    textField(
        text:bind( source:model, sourceProperty:"consumerKey", mutual:true ),
        columns:23,
        constraints:"wrap",
        dragEnabled:true,
        mousePressed:showContextMenu,
    )
    
    label(
        text:"コンシューマシークレット" ,
    )
    textField(
        text:bind( source:model, sourceProperty:"consumerSecret", mutual:true ),
        columns:43,
        constraints:"wrap",
        dragEnabled:true,
        mousePressed:showContextMenu,
    )

    panel(
        constraints:"split 2",
    ) {
        button(
            text:"保存",
            actionPerformed:controller.saveApiSettings,
        )
        
        button(
            text:"キャンセル",
            actionPerformed:controller.cancelApiSettings,
        )
    }
}

dialog(
    id:"pinInputDialog",
    title:"${model.appName} - PINコード入力",
    defaultCloseOperation:WindowConstants.DISPOSE_ON_CLOSE,
    modal:true,
    resizable:false,
    pack: true,
    preferredSize:[280,110],
    owner:view.mainFrame,
    windowClosing:{ evt ->
        model.pin = ""
        this.visible = false
    }
) {
    migLayout(
        layoutConstraints:"fill",
        columnConstraints:"[grow][grow]",
        rowConstraints:"[][grow]",
    )

    label(
        text:"PINコード" ,
    )
    textField(
        text:bind( source:model, sourceProperty:"pin", mutual:true ),
        columns:7,
        constraints:"wrap",
        dragEnabled:true,
        mousePressed:showContextMenu,
    )
    
    panel(
        constraints:"split 2",
    ) {
        button(
            text:"保存",
            actionPerformed:controller.savePin,
        )
        
    }
}
