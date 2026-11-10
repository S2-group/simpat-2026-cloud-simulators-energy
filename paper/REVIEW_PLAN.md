## SIMPAT Paper on Cloud Simulators

## The full list of things I have to do
### R1.4
1. While simulation runtime serves as an objective metric, documentation quality and ease‑of‑adoption are subjectively judged by a single researcher. The validity section mentions that the authors attempted to contact the simulator maintainers for verification, yet maintainers of only three simulators responded. This limits the credibility of the subjective evaluation results.

Yes + add the fact that a second researcher (Max) thoroughly double-checked all assessments of the first researcher and updated the assessments as follows… to be completed based on your results, Max. 

Update also the Study Design section about this.
### R1.5
2. Figures 6 and 7 contain multiple overlapping curves with insufficient color differentiation, making it difficult for readers to distinguish the curves corresponding to each simulator‑power‑model combination. It is suggested to adjust the color scheme, add subplot captions, or split the plots into separate sub‑figures.

Split each plot into a set of plots, one for each Simulator (and then internally to each plot we plot the various power models)

### R1.7
3. Figure 9, the summary overview diagram, is highly valuable. It is recommended to provide brief interpretations for each metric shown in Figure 9 within the main text, rather than merely presenting the figure and leaving its interpretation entirely to readers.

Move Figure 9 in Section 8 (Discussion) and there add a new subsection called “Main Implications” containing a brief paragraph for each block of the figure, giving a high-level overview of what is its main message for potential users of the simulators. Here, I am thinking of max one page for the whole new subsection.

### R1.8
4. Please carefully check and verify the formatting of the references.
Max
-

### R2.3
5. The authors repeat each treatment 30 times but take only the four most similar to the median energy for simulation traces to remove natural system variation. This can skew the ground truth towards an artificially clean workload, leading to a better perceived accuracy by simulators. The justification of this choice should be justified quantitatively, results obtained with all 30 compared to the 4 for which the choice was made, and the influence on the estimated MRE should be discussed.

Can you look into this in terms of feasibility?

### R2.8
6. Figures 5-8 contain substantial information, but the manuscript would benefit from clearer legends, consistent terminology, and explicit indication of confidence intervals or variability where relevant.

Add confidence intervals in all the new plots. This is also partially addressed in comment R1.8 (refer to it in the response to the reviewers).

## Some feedback from Ivano I want to save before his unpaid slack chat prevents me from reading my messages again

### My Original Points
R1.5 - Maybe we just want to drop this section (ease of use), or at least maybe minimize it as an anecdote. Its subjective, and that makes it hard even with whatever changes we make for this to be approved. Plus the main meat of the paper is the performance analysis.  
R1.7 - I dont see the table he is talking about, it looks fine to me. There is a slight overlap between the check marks and the top line, maybe he means this?  
R2.4 - I can run an analysis to justify this with numbers, should be pretty simple. Heres hoping the setup still works ![:slightly\_smiling\_face:](https://a.slack-edge.com/production-standard-emoji-assets/16.0/google-medium/1f642@2x.png)
### Ivanos Response
About your specific comments:  

*   R1.5: I think we should keep this part of the study. I know if risks to be subjective, but if we do as I suggest we mitigate this risk. So, what I suggest is the following: you double check each individual aspect identified by Meret for RQ2 (eg, simulation process, documentation, etc) and you keep note whether you agree with her evaluation or not (and why not). Then, I will act as arbiter for resolving all conflicting evaluations. In this way, the subjectivity is mitigated since it's 3 researchers being involved in the evaluation, instead of only one
*   R1.7: Maybe yes, not sure either
*   R2.4:  ok, try and let me know
  
Please, remember to highlight all the new and updated text in the paper using the `\rone{}` and `\rtwo{}` commands. Also, remember to be ad thorough and polite as possible in the response letter. Typically, I write the part of response letter as soon as I finish working on each individual comment

