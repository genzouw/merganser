import javax.persistence.*

@Entity class AppSettings {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    public Long id

    public String consumerKey = ""

    public String consumerSecret = ""

    public Integer waitingSeconds = 300

    public Integer randomWaitingSeconds = 60
}

