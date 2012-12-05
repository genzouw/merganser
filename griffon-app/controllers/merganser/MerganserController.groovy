package merganser

import ca.odell.glazedlists.*
import ca.odell.glazedlists.gui.*
import ca.odell.glazedlists.swing.*
import com.restfb.*
import com.restfb.types.*
import groovy.sql.*
import groovy.util.logging.Slf4j
import java.awt.*
import javax.swing.*
import org.hibernate.cfg.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import twitter4j.*
import twitter4j.auth.*

@Slf4j
class MerganserController {
    static final int UNFOLLOW_MORATORIUM_DAYS = 3

    static final int SLEEP_TIMES_FOR_UPDATE_BLACK_LIST = 2000

    boolean yesOptionIsNotSelected( message, initFocus ) {
        return (JOptionPane.showOptionDialog(
            view.mainFrame,
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
        ) != JOptionPane.YES_OPTION)
    }

	// these will be injected by Griffon
	def model
	def view

    def factory

	// void mvcGroupInit(Map args) {
	//    // this method is called after model and view are injected
	// }

	// void mvcGroupDestroy() {
	//    // this method is called when the group is destroyed
	// }

	/*
	    Remember that actions will be called outside of the UI thread
	    by default. You can change this setting of course.
	    Please read chapter 9 of the Griffon Guide to know more.

	def action = { evt = null ->
	}
	*/
	def onStartupEnd = { app ->
        log.info "onStartupEnd - start."

        model.statusbarText = "画面を表示します..."

        def config = new AnnotationConfiguration()
        config.with{
            setProperties([
                "hibernate.dialect":"org.hibernate.dialect.HSQLDialect",
                "hibernate.connection.driver_class":"org.hsqldb.jdbcDriver",
                "hibernate.connection.url":"jdbc:hsqldb:file:./merganser.db",
                "hibernate.connection.username":"sa",
                "hibernate.connection.password":"",
                "hibernate.connection.pool_size":"1",
//                "hibernate.connection.autocommit":"true",
                "hibernate.cache.provider_class":"org.hibernate.cache.NoCacheProvider",
                "hibernate.hbm2ddl.auto":"update",
//                "hibernate.show_sql":"true",
                "hibernate.show_sql":"false",
                "hibernate.transaction.factory_class":"org.hibernate.transaction.JDBCTransactionFactory",
                "hibernate.current_session_context_class":"thread",
            ] as Properties)
            addAnnotatedClass(AppSettings)
            addAnnotatedClass(TwitterAccount)
            addAnnotatedClass(TwitterAccountInBlackList)
            addAnnotatedClass(TwitterAccountInFollowingList)
        }

        this.factory = config.buildSessionFactory()

        doOutside{
            if (!Lock.getLock()) {
                edt{
                    JOptionPane.showMessageDialog( view.mainFrame, "${model.appName}が既に起動しています。" )
                }
                System.exit(1)
            }

            def session = factory.currentSession

            def tx = session.beginTransaction()
            def appSettingsList = session.createCriteria(AppSettings.class).list()


            if (!appSettingsList || appSettingsList.empty) {
                JOptionPane.showMessageDialog(view.mainFrame, "はじめにコンシューマキー、コンシューマシークレットを設定してください。")
                edt { 
                    view.apiKeyInputDialog.visible = true
                }


                if (model.consumerKey.empty || model.consumerSecret.empty) {
                    JOptionPane.showMessageDialog( view.mainFrame, "${model.appName}を終了します。" )  
                    System.exit(0)
                }

                session.save(new AppSettings(
                    "consumerKey":model.consumerKey,
                    "consumerSecret":model.consumerSecret,
                    "waitingSeconds":300,
                    "randomWaitingSeconds":60,
                ))

                appSettingsList = session.createQuery("from AppSettings").list()
            }

            model.consumerKey = appSettingsList[0].consumerKey
            model.consumerSecret = appSettingsList[0].consumerSecret
            model.waitingSeconds = appSettingsList[0].waitingSeconds?.toString() ?: 300
            model.randomWaitingSeconds = appSettingsList[0].randomWaitingSeconds?.toString() ?: 60

            def now = Calendar.instance.time
            session.createQuery("from TwitterAccount").list().findAll {
                it.lastFollowDate?.format("yyyyMMdd") != now?.format("yyyyMMdd") || it.todayFollowCount == null
            }.each{
                it.lastFollowDate = now
                it.todayFollowCount = 0
                session.save(it)
            }

            tx.commit()

            this.reflushTwitterAccount(null)

            edt{
                model.with{
                    statusbarText = "画面を表示しました。"
                    editable = true
                    following = false
                    editing = false
                    followingOrEditing = ( following || editing )
                    editButtonEnabled = !followingOrEditing && !twitterAccountForComboBoxEventList.empty
                }
                model.snapshot = model.properties.clone()
            }

        }
	}

    String createAccountInfoText() {
        return "【${model.twitterAccountScreenName}】${model.twitterAccountNickName ? "：【" + model.twitterAccountNickName + "】" : ""}"
    }

	def deleteTwitterAccount = { evt ->
        log.info "deleteTwitterAccount - start."

        def accountInfoText = createAccountInfoText()
        if (yesOptionIsNotSelected(
                "${accountInfoText}を削除します。\nよろしいですか？",
                "いいえ"
            ))
            return

        model.with{

            def session = factory.currentSession

            def tx = session.beginTransaction()

            def twitterAccount = session.get(TwitterAccount.class, twitterAccountId)

            twitterAccount.twitterAccountsInFollowingList.each{
                session.delete(it)
            }

            twitterAccount.twitterAccountsInBlackList.each{
                session.delete(it)
            }

            session.delete(twitterAccount)

            editing = false
            followingOrEditing = ( following || editing )
            editButtonEnabled = !followingOrEditing && !twitterAccountForComboBoxEventList.empty

            tx.commit()
	    }

        this.reflushTwitterAccount(evt)

        this.cancelTwitterAccount(evt)

        model.statusbarText = "${accountInfoText}を削除しました。"

        log.info "deleteTwitterAccount - end."
	}

	def updateTwitterAccount = { evt ->
        log.info "updateTwitterAccount - start."

       
        def valid = true
        edt {
            def dialog = JOptionPane.&showMessageDialog
            if ((model.twitterAccountSearchTweetKeywords ?: "").empty &&
                    (model.twitterAccountSearchTweetKeywords2 ?: "").empty &&
                    (model.twitterAccountSearchTweetKeywords3 ?: "").empty
            ) {
                dialog(
                    view.mainFrame,
                    "[ツイートの検索キーワード]を入力してください。" ,
                )
                valid = false
                return
            }

            if (!( model.twitterAccountMaxFollowCountForOneDay ?: "" ).isNumber() ||
                    !( model.twitterAccountMaxFollowCountForOneDay.toInteger() in 1..99 )) {
                model.statusbarText =  ""
                dialog(
                    view.mainFrame,
                    "[一日当たりのフォロー数上限]には1～99の数値を入力してください。",
                )
                valid = false
                return
            }

            if (!( model.waitingSeconds ?: "" ).isNumber() ||
                    !( model.waitingSeconds.toInteger() in 30..999 )) {
                model.statusbarText =  ""
                dialog(
                    view.mainFrame,
                    "[自動フォロー実行間隔]には30～999の数値を入力してください。",
                )
                valid = false
                return
            }

            if (!( model.randomWaitingSeconds ?: "" ).isNumber() ||
                    !( model.randomWaitingSeconds.toInteger() in 0..999 )) {
                model.statusbarText =  ""
                dialog(
                    view.mainFrame,
                    "[揺らぎ間隔]には0～999の数値を入力してください。",
                )
                valid = false
                return
            }
        }

        if (!valid )
            return

        if (model.waitingSeconds?.toInteger() < 300)
            JOptionPane.showOptionDialog(
                view.mainFrame,
                "300秒より少なく設定しフォローを行った場合、IDが凍結・または削除される恐れがあります。",
                "警告",
                JOptionPane.OK_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                [ "閉じる" ] as String[], "閉じる"
            )
            


        model.with{

            def session = factory.currentSession

            def tx = session.beginTransaction()

            def twitterAccount = session.get(TwitterAccount.class, twitterAccountId)

            twitterAccount.screenName = twitterAccountScreenName

            twitterAccount.doFollow = twitterAccountDoFollow
            twitterAccount.searchTweetKeywords = (twitterAccountSearchTweetKeywords?:"").replaceAll(
                /　/, " "
            ).replaceAll(
                / +/, " "
            )
            twitterAccount.searchTweetKeywords2 = (twitterAccountSearchTweetKeywords2?:"").replaceAll(
                /　/, " "
            ).replaceAll(
                / +/, " "
            )
            twitterAccount.searchTweetKeywords3 = (twitterAccountSearchTweetKeywords3?:"").replaceAll(
                /　/, " "
            ).replaceAll(
                / +/, " "
            )

            twitterAccount.doRefollow = twitterAccountDoRefollow

            twitterAccount.doRefollowInBlackList = twitterAccountDoRefollowInBlackList
            twitterAccount.maxFollowCountForOneDay = twitterAccountMaxFollowCountForOneDay.toInteger()
            twitterAccount.lastUpdated = Calendar.instance.time

            editing = false
            followingOrEditing = ( following || editing )
            editButtonEnabled = !followingOrEditing && !twitterAccountForComboBoxEventList.empty

            session.save(twitterAccount)

            def appSettings = session.createQuery("from AppSettings").list()[0]
            appSettings.waitingSeconds = waitingSeconds.toInteger()
            appSettings.randomWaitingSeconds = randomWaitingSeconds.toInteger()

            session.save(appSettings)

            tx.commit()
	    }

        this.reflushTwitterAccount(evt)

        this.cancelTwitterAccount(evt)

        model.statusbarText = "保存が完了しました。"

        log.info "updateTwitterAccount - end."
	}

	def insertTwitterAccount = { evt ->
        log.info "insertTwitterAccount - start."

        model.with{

            def twitter = new TwitterFactory().getInstance();
            twitter.setOAuthConsumer(
                model.consumerKey,
                model.consumerSecret
            )

            RequestToken requestToken = twitter.getOAuthRequestToken()

            Desktop.desktop.browse new URI(requestToken.getAuthorizationURL())

            model.pin = ""
            
            JOptionPane.showMessageDialog( view.mainFrame, "ブラウザからTwitterにログインし、取得したPINコードを入力してください。" )
            view.pinInputDialog.visible = true


            if (!model.pin || model.pin.empty) {
                JOptionPane.showMessageDialog( view.mainFrame, "正しいPINコードが入力されませんでした。" )
                return
            }

			AccessToken accessToken = null
            try {
			    accessToken = twitter.getOAuthAccessToken(requestToken, model.pin);
            } catch ( org.codehaus.groovy.runtime.InvokerInvocationException e ) {
                JOptionPane.showMessageDialog( view.mainFrame, "正しいPINコードが入力されませんでした。" )
                return
            }

            def session = factory.currentSession

            def tx = session.beginTransaction()

            def user = twitter.showUser(twitter.id)

            def imageUrl = user.profileImageUrl
            
            def twitterAccountList = session.createQuery("from TwitterAccount").list()
            if (twitterAccountList.find{
                it.accountId == twitter.id
            }) {
                JOptionPane.showMessageDialog( view.mainFrame, "同じアカウントがすでに登録されています。" )
                return
            }

            def friendIds = twitter.getFriendsIDs(-1)?.getIDs()?.toList() ?: []

            def now = Calendar.instance.time
            session.save(new TwitterAccount(
                nickName:twitter.screenName == user.name ? "" : user.name,
                screenName:twitter.screenName,
                accountId:twitter.id,
                token:accessToken.token,
                tokenSecret:accessToken.tokenSecret,
                profileImageUrl:imageUrl?:"",
                totalFollowCount:( friendIds?.size() ?: 0 ),
                lastUpdated:Calendar.instance.time,
                maxFollowCountForOneDay:20,
                lastFollowDate:now,
                todayFollowCount:0,
            ))

            tx.commit()

        }

        this.reflushTwitterAccount(evt)

        this.cancelTwitterAccount(evt)

        model.statusbarText = "アカウントを追加しました。"

        model.snapshot = model.properties.clone()

        log.info "insertTwitterAccount - end."
	}

    def isModified() {
        log.info "isModified - start."
        return model.snapshot.selectedTwitterAccount != null && !(
            model.twitterAccountDoFollow == model.snapshot.twitterAccountDoFollow &&
            model.twitterAccountSearchTweetKeywords == model.snapshot.twitterAccountSearchTweetKeywords &&
            model.twitterAccountSearchTweetKeywords2 == model.snapshot.twitterAccountSearchTweetKeywords2 &&
            model.twitterAccountSearchTweetKeywords3 == model.snapshot.twitterAccountSearchTweetKeywords3 &&
            model.twitterAccountDoRefollow == model.snapshot.twitterAccountDoRefollow &&
            model.twitterAccountMaxFollowCountForOneDay == model.snapshot.twitterAccountMaxFollowCountForOneDay &&
            model.waitingSeconds == model.snapshot.waitingSeconds &&
            model.randomWaitingSeconds == model.snapshot.randomWaitingSeconds &&
            model.twitterAccountDoRefollowInBlackList == model.snapshot.twitterAccountDoRefollowInBlackList &&
            model.twitterAccountDoFollow == model.snapshot.twitterAccountDoFollow
        )
    }

	def cancelTwitterAccount = { evt ->
        log.info "cancelTwitterAccount - start."

        model.with{

            selectedTwitterAccount = null

            twitterAccountId = null
            twitterAccountScreenName = null

            twitterAccountDoFollow = false
            twitterAccountSearchTweetKeywords = ""
            twitterAccountSearchTweetKeywords2 = ""
            twitterAccountSearchTweetKeywords3 = ""
            twitterAccountMaxFollowCountForOneDay = ""

            twitterAccountDoRefollow = false

            def session = factory.currentSession
            def tx = session.beginTransaction()
            def appSettings = session.createQuery("from AppSettings").list()[0]
            waitingSeconds = appSettings.waitingSeconds?.toString() ?: "300"
            randomWaitingSeconds = appSettings.randomWaitingSeconds?.toString() ?: "60"
            tx.commit()

            twitterAccountDoRefollowInBlackList = false

            editing = false
            followingOrEditing = ( following || editing )
            editButtonEnabled = !followingOrEditing && !twitterAccountForComboBoxEventList.empty

            statusbarText = "キャンセルしました。"

	    }
        model.snapshot = model.properties.clone()

        log.info "cancelTwitterAccount - end."
	}

	def selectTwitterAccount = { evt ->
        log.info "selectTwitterAccount - start."

        log.debug "${selectedTwitterAccount}"
	    if (model.selectedTwitterAccount) {

	        model.with{
                twitterAccountId = selectedTwitterAccount.id
                twitterAccountNickName = selectedTwitterAccount.nickName
                twitterAccountScreenName = selectedTwitterAccount.screenName

                twitterAccountDoFollow = selectedTwitterAccount.doFollow
                twitterAccountSearchTweetKeywords = selectedTwitterAccount.searchTweetKeywords
                twitterAccountSearchTweetKeywords2 = selectedTwitterAccount.searchTweetKeywords2
                twitterAccountSearchTweetKeywords3 = selectedTwitterAccount.searchTweetKeywords3

                twitterAccountDoRefollow = selectedTwitterAccount.doRefollow
                twitterAccountMaxFollowCountForOneDay = selectedTwitterAccount.maxFollowCountForOneDay

                twitterAccountDoRefollowInBlackList = selectedTwitterAccount.doRefollowInBlackList
                editing = true
                followingOrEditing = ( following || editing )
                editButtonEnabled = !followingOrEditing && !twitterAccountForComboBoxEventList.empty
	        }

            model.snapshot = model.properties.clone()
	    }
        log.info "selectTwitterAccount - end."
	}

    def windowClosing = { evt ->
        log.info "windowClosing - start."


        if( JOptionPane.showOptionDialog(
                view.mainFrame,
                "【${model.appName}】を終了します。\nよろしいですか？",
                "確認",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                [
                    "はい",
                    "いいえ"
                ] as String[],
                "いいえ",
            ) == JOptionPane.YES_OPTION ) {

            if (isModified() && JOptionPane.showOptionDialog(
                        view.mainFrame,
                        "保存していない設定があります。\n【${model.appName}】を終了すると設定が失われます。終了してもよろしいですか？",
                        "確認",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        [
                            "はい",
                            "いいえ"
                        ] as String[],
                        "いいえ",
                ) != JOptionPane.YES_OPTION) {
                return
            }

            JOptionPane.showMessageDialog( view.mainFrame, "お疲れ様でした。" )
            view.mainFrame.dispose()
            System.exit(0)
        }
        log.info "windowClosing - end."
	}

    def setDataSource = { list, dataSource ->
        list.readWriteLock.writeLock().lock()
        try {
            list.clear()
            dataSource.each{
                list.add it
            }
        } finally {
            list.readWriteLock.writeLock().unlock()
        }
    }

	def reflushTwitterAccount = { evt ->
        log.info "reflushTwitterAccount - start."

        def session = factory.currentSession

        def tx = session.beginTransaction()
        def dataList = session.createQuery("from TwitterAccount").list()

        setDataSource( model.twitterAccountEventList, dataList.collect {
            [
                "lastFollowCount":it.lastFollowCount,
                "todayFollowCount":it.todayFollowCount,
                "totalFollowCount":it.totalFollowCount,
                "merganserFollowingCount":it.twitterAccountsInFollowingList.size(),
                "screenName":it.screenName,
                "nickName":it.nickName,
            ]
        } )
        setDataSource( model.twitterAccountForComboBoxEventList, dataList )

        tx.commit()
        log.info "reflushTwitterAccount - end."
	}

	def deleteTwitterAccountsInBlackList = { evt ->
        log.info "deleteTwitterAccountsInBlackList - start."

        def accountInfoText = createAccountInfoText()
        if( yesOptionIsNotSelected(
                "${accountInfoText}の再フォロー禁止リストを削除します。\nよろしいですか？",
                "いいえ"
            ) )
            return

        model.with{

            def session = factory.currentSession

            def tx = session.beginTransaction()

            def twitterAccount = session.get(TwitterAccount.class, twitterAccountId)

            twitterAccount.twitterAccountsInBlackList.each{
                session.delete(it)
            }

            editing = false
            followingOrEditing = ( following || editing )
            editButtonEnabled = !followingOrEditing && !twitterAccountForComboBoxEventList.empty

            tx.commit()
	    }


        this.reflushTwitterAccount(evt)

        this.cancelTwitterAccount(evt)

        model.statusbarText = "${accountInfoText}の再フォロー禁止リストを削除しました。"

        log.info "deleteTwitterAccountsInBlackList - end."
	}

	def showTwitterAccountsInBlackList = { evt ->
        log.info "showTwitterAccountsInBlackList - start."

        edt {
            def session = factory.currentSession

            def tx = session.beginTransaction()
            def account = session.get(TwitterAccount.class, model.selectedTwitterAccount.id)
            account.twitterAccountsInBlackList.each{
                it
            }
            tx.commit()

            edt {
                setDataSource( model.twitterAccountInBlackListEventList, account.twitterAccountsInBlackList )
            }

            view.blackListDialog.visible = true
        }

        log.info "showTwitterAccountsInBlackList - end."
	}

    def startFollow = { evt ->
        log.info "startFollow - start."

        model.following = true
        model.editing = false
        model.followingOrEditing = ( model.following || model.editing )
        model.editButtonEnabled = !model.followingOrEditing && !model.twitterAccountForComboBoxEventList.empty

        edt {
            model.statusbarText = "自動フォローを開始しました。"
        }

        Thread.start {
            // define common logic
            def printMessage = { account, message = "" ->
                edt {
                    if (model.following) {
                        model.statusbarText = "アカウント名:${account.screenName} - ${message}"
                    }
                }
            }

            def refreshSelectedTwitterAccount = { TwitterAccount account ->
                edt {
                    model.with{
                        selectedTwitterAccount  = twitterAccountForComboBoxEventList.find{
                            it.id == account.id
                        }

                        twitterAccountId = account.id
                        twitterAccountNickName = account.nickName
                        twitterAccountScreenName = account.screenName

                        twitterAccountDoFollow = account.doFollow
                        twitterAccountSearchTweetKeywords = account.searchTweetKeywords
                        twitterAccountSearchTweetKeywords2 = account.searchTweetKeywords2
                        twitterAccountSearchTweetKeywords3 = account.searchTweetKeywords3

                        twitterAccountMaxFollowCountForOneDay = account.maxFollowCountForOneDay

                        twitterAccountDoRefollow = account.doRefollow

                        twitterAccountDoRefollowInBlackList = account.doRefollowInBlackList
                    }
                }
            }

            def refreshList = { accountList ->
                edt {
                    setDataSource( model.twitterAccountEventList, accountList.collect {
                        [
                            "lastFollowCount":it.lastFollowCount,
                            "todayFollowCount":it.todayFollowCount,
                            "totalFollowCount":it.totalFollowCount,
                            "merganserFollowingCount":it.twitterAccountsInFollowingList.size(),
                            "screenName":it.screenName,
                            "nickName":it.nickName,
                        ]
                    } )
                    setDataSource( model.twitterAccountForComboBoxEventList, accountList )
                }
            }

            def createTwitterApi = { TwitterAccount account ->
                def api = new TwitterFactory().getInstance()
                api.setOAuthConsumer(
                    model.consumerKey,
                    model.consumerSecret
                )

                api.setOAuthAccessToken(
                    new AccessToken(
                        account.token,
                        account.tokenSecret
                    )
                )

                api
            }

            ArrayList.metaClass.define {
                eachOrSkipAtRemovedAccount{ logic ->
                    delegate.each { account ->
                        try {
                            logic( account )
                        } catch ( twitter4j.TwitterException e ) {
                            def skipMessage = " 処理をスキップします。"
                            if (e.statusCode == 403) {
                                printMessage account, "一時間のAPI呼び出し回数上限を超えた可能性があります。${skipMessage}"
                                sleep 5000
                            } else if (e.statusCode == 404) {
                                printMessage account, "アカウントが見つかりません。${skipMessage}"
                                sleep 5000
                            } else {
                                throw e 
                            }
                        }
                    }
                }
            }

            def session = factory.currentSession
            def tx = session.beginTransaction()
            def daysToSearchSince = 60

            try {
                while( model.following ) {
                    def accountList = session.createQuery("from TwitterAccount").list()

                    if( !accountList.find{ account ->
                        account.doFollow
                    } ) {
                        doOutside {
                            JOptionPane.showOptionDialog(
                                view.mainFrame,
                                "少なくとも１アカウントは自動フォロー機能を有効としてください。",
                                "エラー",
                                JOptionPane.OK_OPTION,
                                JOptionPane.ERROR_MESSAGE,
                                null,
                                [ "閉じる" ] as String[], "閉じる"
                            )
                        }

                        model.following = false
                    }

                    accountList.eachOrSkipAtRemovedAccount{ account ->
                        if (!model.following) return

                        log.info "startFollow - loop for each TwitterAccount ${account.toString()}."

                        refreshSelectedTwitterAccount( account )

                        printMessage account

                        account.lastFollowCount = 0
                        session.update(account)
                        account = session.get(TwitterAccount.class, account.id)

                        printMessage account, "【フォローリスト更新中】"

                        updateFollowingList( createTwitterApi( account ), account, session )

                        refreshList accountList
                    }

                    accountList.eachOrSkipAtRemovedAccount{ account ->
                        if (!model.following) return

                        refreshSelectedTwitterAccount( account )

                        if (!account.doRefollowInBlackList) {
                            printMessage account, "【再フォロー禁止リスト更新中】"

                            def now = Calendar.instance.time

                            def unrefollorAccountList = account.twitterAccountsInFollowingList.findAll{
                                it.lastUpdated <= (now - UNFOLLOW_MORATORIUM_DAYS)
                            }

                            for( def i = 0; i < unrefollorAccountList.size(); i++ ) {
                                if (!model.following) return false

                                def unrefollorAccount = unrefollorAccountList[i]

                                printMessage account, "【再フォロー禁止リスト更新中】 ${i+1}/${unrefollorAccountList.size()}処理中 】"

                                createTwitterApi( account ).destroyFriendship(unrefollorAccount.accountId)

                                sleep SLEEP_TIMES_FOR_UPDATE_BLACK_LIST

                                if (!account.twitterAccountsInBlackList*.accountId.contains(unrefollorAccount.accountId) ) {
                                    def newBlackAccount = new TwitterAccountInBlackList(
                                        accountId:unrefollorAccount.accountId,
                                        twitterAccount:account,
                                        lastUpdated:Calendar.instance.time
                                    )

                                    session.save(newBlackAccount)

                                    account.twitterAccountsInBlackList << newBlackAccount
                                }
                                
                                account.twitterAccountsInFollowingList.remove unrefollorAccount

                                account.totalFollowCount--

                                session.save(account)

                                refreshList accountList

                            }

                            def blackList = account.twitterAccountsInBlackList.findAll{
                                !it.screenName || it.screenName.empty
                            }

                            if (!blackList.empty) {
                                sleep 3000;
                                createTwitterApi( account ).lookupUsers(blackList*.accountId as long[])?.each{ user ->
                                    def blackUser = blackList.find{
                                        it.accountId == user.id
                                    }
                                    blackUser.screenName = user.screenName
                                    blackUser.nickName = user.name
                                    session.save(blackUser)
                                }

                            }
                            
                        }
                    }

                    def follow = { api, account, id, accounts ->
                        log.info "startFollow - follow - start"

                        if ( account.totalFollowCount > 2000 ) {
                            printMessage account, "フォローできる上限に達しております。"
                            sleep 10000
                            return true
                        }

                        api.createFriendship( id )

                        def newTwitterAccount = new TwitterAccountInFollowingList(
                            accountId:id,
                            twitterAccount:account,
                            lastUpdated:Calendar.instance.time
                        )

                        session.save(newTwitterAccount)

                        account.twitterAccountsInFollowingList << newTwitterAccount
                        account.totalFollowCount++
                        account.lastFollowCount++
                        account.todayFollowCount++
                        session.update(account)

                        refreshList accounts

                        def sleepSeconds = (
                            model.waitingSeconds.toInteger() +
                            Math.random() * model.randomWaitingSeconds.toInteger()
                        ).toInteger()

                        for( def i in 0..<sleepSeconds ) {
                            if (!model.following) return false
                            sleep(1000)
                        }

                        log.info "startFollow - follow - end"
                        return true
                    }

                    def unfollowingIdsMap = [:]
                    accountList.eachOrSkipAtRemovedAccount{ account ->
                        if (!model.following) return

                        if (!account.doFollow) return


                        printMessage account, "【自動フォロー中】"

                        refreshSelectedTwitterAccount( account )

                        def api = createTwitterApi( account )

                        for( def keyword in [
                            account.searchTweetKeywords,
                            account.searchTweetKeywords2,
                            account.searchTweetKeywords3,
                        ].findAll{
                            !it.isEmpty()
                        } ) {

                            def tweets = null
                            try{ 
                                tweets = api.search(
                                    new Query(
                                        query:keyword,
                                        rpp:100,
                                        since:(Calendar.instance.time - daysToSearchSince).format("yyyy-MM-dd"),
                                        page:1
                                    )
                                )?.tweets
                            } catch ( Exception e ){
                                log.error "", e
                            }

                            if (!tweets) {
                                sleep 3 * 1000
                                continue
                            }

                            def tweetedScreenNames = tweets*.fromUser
                            if (!tweetedScreenNames) {
                                sleep 3 * 1000
                                continue
                            }

                            def tweetedUserIds = null
                            try {
                                tweetedUserIds = api.lookupUsers(tweetedScreenNames as String[])*.id
                            } catch ( Exception e ) {
                                log.error "", e
                            }

                            if (!tweetedUserIds) {
                                sleep 3 * 1000
                                continue
                            }

                            sleep 1 * 1000
                            def unfriendIds = tweetedUserIds - ( api.getFriendsIDs(-1)?.getIDs()?.toList() ?: [] ) 
                            sleep 1 * 1000

                            def blackListIds = account.doRefollowInBlackList ?  [] : (
                                account.twitterAccountsInBlackList*.accountId
                            )

                            def newFriendIds = ( unfriendIds - blackListIds ).findAll{ it != account.accountId  }

                            def limit = ( account.maxFollowCountForOneDay - account.todayFollowCount )
                            if (limit < 0) {
                                limit = 0
                            }
                            if (newFriendIds.size() > limit )
                                newFriendIds = newFriendIds[0..<limit]
                            unfollowingIdsMap[account] = newFriendIds
                            break
                        }
                    }

                    unfollowingIdsMap.values().collect{ it.size() }?.max()?.times{ i ->
                        unfollowingIdsMap.each{ account, newFriendIds ->
                            if (!model.following) return

                            if (newFriendIds.size() <= i )
                                return

                            refreshSelectedTwitterAccount( account )

                            printMessage account, "【自動フォロー中 ${i+1}/${newFriendIds.size()}処理中 】"

                            try {
                                if (!follow(createTwitterApi( account ), account, newFriendIds[i], accountList))
                                    return
                                printMessage account, "【自動フォロー中 ${i+1}/${newFriendIds.size()}済 】"
                            } catch ( e ) {
                                log.error "フォロー実行時にエラーが発生しました。 newFriendId=${newFriendIds[i]}", e
                                sleep 10000
                            }
                        }
                    }

                    unfollowingIdsMap = [:]
                    accountList.eachOrSkipAtRemovedAccount{ account ->
                        if (!model.following) return

                        if (!account.doRefollow) return

                        printMessage account, "【自動リフォロー中】"

                        def api = createTwitterApi( account )
                        def friendIds = api.getFriendsIDs(-1)?.getIDs()?.toList() ?: []
                        def followerIds = api.getFollowersIDs(-1)?.getIDs()?.toList() ?: []
                        
                        def unfriendIds = followerIds - friendIds 
                        def blackListIds = account.doRefollowInBlackList ?  [] : (
                            account.twitterAccountsInBlackList*.accountId
                        )

                        def newFriendIds = unfriendIds - blackListIds
                        if (!newFriendIds.empty)
                            newFriendIds = newFriendIds.findAll{ it != account.accountId  }

                        def limit = ( account.maxFollowCountForOneDay - account.todayFollowCount )
                        if (limit < 0) {
                            limit = 0
                        }
                        if (newFriendIds.size() > limit)
                            newFriendIds = newFriendIds[0..<limit]
                        unfollowingIdsMap[account] = newFriendIds
                    }

                    unfollowingIdsMap.values().collect{ it.size() }?.max()?.times{ i ->
                        unfollowingIdsMap.each{ account, newFriendIds ->
                            if (!model.following) return

                            if (newFriendIds.size() <= i) return

                            refreshSelectedTwitterAccount( account )

                            printMessage account, "【自動リフォロー中 ${i+1}/${newFriendIds.size()}処理中 】"

                            try {
                                if (!follow(createTwitterApi( account ), account, newFriendIds[i], accountList))
                                    return
                                printMessage account, "【自動リフォロー中 ${i+1}/${newFriendIds.size()}済 】"
                            } catch ( e ) {
                                log.error "リフォロー実行時にエラーが発生しました。 newFriendId=${newFriendIds[i]}", e
                                sleep 10000
                            }
                        }
                    }

                    def now = Calendar.instance.time
                    def tomorrow = Date.parse("yyyy/MM/dd HH:mm", (now+1).format("yyyy/MM/dd 00:00"))

                    model.statusbarText = "翌日まで処理を待機します..." 

                    def secondsOfWaiting = ( ( tomorrow.time - now.time )/1000 ).toInteger()
                    log.info "翌日までの待機時間：${secondsOfWaiting}s"
                    log.info "翌日までの待機時間：${secondsOfWaiting/60}m"
                    secondsOfWaiting.times{
                        if (model.following)
                            sleep 1000
                    }
                    

                    if (model.following) {
                        accountList.findAll {
                            it.lastFollowDate?.format("yyyyMMdd") != tomorrow?.format("yyyyMMdd") || it.todayFollowCount == null
                        }.each{
                            it.lastFollowDate = tomorrow
                            it.todayFollowCount = 0
                            session.save(it)
                        }
                    }
                }
            } finally {
                if (!tx.wasCommitted()) {
                    tx.commit()
                }
            }

            edt {
                model.with{

                    following = false
                    followingOrEditing = ( following || editing )
                    editButtonEnabled = !followingOrEditing && !twitterAccountForComboBoxEventList.empty

                    selectedTwitterAccount = null

                    twitterAccountId = null
                    twitterAccountScreenName = null

                    twitterAccountDoFollow = false
                    twitterAccountSearchTweetKeywords = ""
                    twitterAccountSearchTweetKeywords2 = ""
                    twitterAccountSearchTweetKeywords3 = ""

                    twitterAccountDoRefollow = false

                    twitterAccountDoRefollowInBlackList = false
                }
                model.snapshot = model.properties.clone()

                model.statusbarText = "自動フォローを終了しました。"

            }
	    }

        log.info "startFollow - end."
    }

    def updateFollowingList( api, account, session ) {
        log.info "updateFollowingList - start."
        def friendIds = api.getFriendsIDs(-1)?.getIDs()?.toList() ?: []
        def followerIds = api.getFollowersIDs(-1)?.getIDs()?.toList() ?: []
        
        account.totalFollowCount = friendIds.size() ?: 0
        account.lastUpdated = Calendar.instance.time

        // twitter上からフォロー解除されているユーザー情報は除去
        account.twitterAccountsInFollowingList.findAll {
            !friendIds.contains(it.accountId)
        }.each{ unfollowingAccout ->
            account.twitterAccountsInFollowingList.remove unfollowingAccout
        }

        account.twitterAccountsInFollowingList.findAll {
            friendIds.contains(it.accountId)
        }.each { followingAccount ->
            followingAccount.lastUpdated = Calendar.instance.time
        }

        session.save(account)

        log.info "updateFollowingList - end."
    }

    def endFollow = { evt ->
        log.info "endFollow - start."

        model.following = false
        model.editing = false
        model.followingOrEditing = ( model.following || model.editing )
        model.editButtonEnabled = !model.followingOrEditing && !model.twitterAccountForComboBoxEventList.empty
        model.statusbarText = "自動フォローを停止中です."

        doOutside { 
            while ( model.selectedTwitterAccount != null ) {
                model.statusbarText += "."
                sleep 1000
            }
            model.statusbarText = "自動フォローを停止しました。"
        }

        log.info "endFollow - end."
    }

    def saveApiSettings = { evt = null ->
        log.info "saveApiSettings - start."
        try {
            doOutside {
                def errors = []
                
                edt {
                    model.consumerKey = model.consumerKey.trim()
                    model.consumerSecret = model.consumerSecret.trim()

                    if (model.consumerKey.empty) {
                        errors << "コンシューマキーは必ず入力してください。"
                    }

                    if (model.consumerSecret.empty) {
                        errors << "コンシューマシークレットは必ず入力してください。"
                    }

                    if (!errors.empty) {
                        JOptionPane.showOptionDialog(
                            view.mainFrame,
                            errors.join("\n"),
                            "エラー",
                            JOptionPane.OK_OPTION,
                            JOptionPane.ERROR_MESSAGE,
                            null,
                            [ "閉じる" ] as String[], "閉じる"
                        )
                    } else {
                        view.apiKeyInputDialog.visible = false
                    }
                }
            }
        } catch ( e ) {
            log.error "saveApiSettings - exception.", e
        } finally {
            log.info "saveApiSettings - end."
        }
    }

    def cancelApiSettings = { evt = null ->
        log.info "cancelApiSettings - start."
        try {
            doOutside {
                edt {
                    model.consumerKey = ""
                    model.consumerSecret = ""
                    view.apiKeyInputDialog.visible = false
                }
            }
        } catch ( e ) {
            log.error "cancelApiSettings - exception.", e
        } finally {
            log.info "cancelApiSettings - end."
        }
    }
    
    def savePin = { evt = null ->
        log.info "savePin - start."
        try {
            
    
            doOutside {
                def errors = []
                
                edt {
                    model.pin = model.pin.trim()

                    if (model.pin.empty) {
                        errors << "PINコードは必ず入力してください。"
                    }

                    if (!errors.empty) {
                        JOptionPane.showOptionDialog(
                            view.mainFrame,
                            errors.join("\n"),
                            "エラー",
                            JOptionPane.OK_OPTION,
                            JOptionPane.ERROR_MESSAGE,
                            null,
                            [ "閉じる" ] as String[], "閉じる"
                        )
                    } else {
                        view.pinInputDialog.visible = false
                    }
                }
            }
        } catch ( e ) {
            log.error "savePin - exception.", e
        } finally {
            log.info "savePin - end."
        }
    }
    
}

