import javax.persistence.*

@Entity class TwitterAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    public Long id

    public Long accountId

    public String nickName = ""

    public String screenName = ""

    public String icon = ""

    public String token = ""

    public String tokenSecret = ""

    public String profileImageUrl = ""

    public Boolean doFollow = false

    public String searchTweetKeywords = ""

    public String searchTweetKeywords2 = ""

    public String searchTweetKeywords3 = ""

    public Boolean doRefollow = true

    public Boolean doRefollowInBlackList = false

    public Integer totalFollowCount = null

    public Integer lastFollowCount = 0

    public Integer maxFollowCountForOneDay = 20 

    public Date lastFollowDate = new Date()

    public Integer todayFollowCount = 0

    @OneToMany(targetEntity = TwitterAccountInBlackList.class, mappedBy = "twitterAccount")
    public List<TwitterAccountInBlackList> twitterAccountsInBlackList = new ArrayList<TwitterAccountInBlackList>()

    @OneToMany(targetEntity = TwitterAccountInFollowingList.class, mappedBy = "twitterAccount")
    public List<TwitterAccountInFollowingList> twitterAccountsInFollowingList = new ArrayList<TwitterAccountInFollowingList>()

    public Date lastUpdated = null

}

