package merganser

import groovy.beans.Bindable
import ca.odell.glazedlists.*

class MerganserModel {
    final String appName = "merganser"

    @Bindable TwitterAccount selectedTwitterAccount = null

    @Bindable Long twitterAccountId = null

    @Bindable String twitterAccountScreenName = ""

    @Bindable String twitterAccountNickName = ""

    @Bindable String twitterAccountIcon = ""

    @Bindable Boolean twitterAccountDoFollow = true

    @Bindable String twitterAccountSearchTweetKeywords = ""

    @Bindable String twitterAccountSearchTweetKeywords2 = ""

    @Bindable String twitterAccountSearchTweetKeywords3 = ""

    @Bindable Boolean twitterAccountDoRefollow = true

    @Bindable String twitterAccountMaxFollowCountForOneDay = "20"

    @Bindable String waitingSeconds = "300"

    @Bindable String randomWaitingSeconds = "60"

    @Bindable Boolean twitterAccountDoRefollowInBlackList = false

    @Bindable String statusbarText = ""

    @Bindable Boolean editable = false

    @Bindable Boolean editing = false

    @Bindable Boolean following = false

    @Bindable Boolean followingOrEditing = false

    @Bindable Boolean editButtonEnabled = true

    @Bindable String consumerKey = ""

    @Bindable String consumerSecret = ""

    @Bindable String pin = ""

//    EventList twitterAccountEventList = new SortedList(
//        new BasicEventList(),
//        { a, b -> b.updatedTime <=> a.updatedTime } as Comparator
//    )

    EventList twitterAccountEventList = new BasicEventList()

    EventList twitterAccountForComboBoxEventList = new BasicEventList()

    EventList twitterAccountInBlackListEventList = new BasicEventList()

    Map snapshot = [:]

}
