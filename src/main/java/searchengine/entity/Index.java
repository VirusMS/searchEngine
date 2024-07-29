package searchengine.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "website_index")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "page_id", columnDefinition = "integer", nullable = false)
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", columnDefinition = "integer", nullable = false)
    private Lemma lemma;

    @Column(name = "lemma_rank", columnDefinition = "FLOAT", nullable = false)
    private Float rank;

}
