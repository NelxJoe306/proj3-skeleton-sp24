# **Project 2B/C: Design Document**

| Name | class id | Email |
| ----- | ----- | ----- |
| Your Name | sp24-s\*\*\* | your.email@berkeley.edu |

## **Data Structures**

Data structures you are planning to use and why. Include any potential helper classes here. If you think something will require code repetition, state how you will mitigate it.

Example (if this was for HW2):

* I will use the boolean\[\]\[\] 2D array to keep track of the number of sites opened.  
* I will use the WQU data structure to connect my sites together.

---

## **Algorithms**

Pseudocode and general logic behind your implementation, and why. Include any potential helper methods here. If you think something will require code repetition, state how you will mitigate it.

**Hyponyms (single-word case)**

* I will … in order to ...

**Hyponyms (multi-word case)**

* …

**Common Ancestors (single-word case)**

* …

**Common Ancestors (multi-word case)**

*  …

**k \> 0**

* …

Example (if this was for HW2):

* I will have virtual sites that will allow me to have quicker runtimes because I don’t need to check every single cell in the top or bottom row for connectedness.  
* I will create a `xyto1D` helper function for my implementation to make it easier to convert between the 2D grid coordinates and the 1D coordinates of the WQU. This ensures that I don’t have to repeat myself as much across my implementation in this coordinate conversion. Some pseudocode is shown below:

```py
xyto1D(int x, int y):
Converts the x and y coordinates inputted into a 1D coordinate that can be used in the WQU. This is done by doing...
```

…