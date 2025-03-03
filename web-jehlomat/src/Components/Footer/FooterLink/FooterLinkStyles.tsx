import styled, {createGlobalStyle} from "styled-components";
import {white} from "./../../Utils/Colors";

export const Container = styled.div`
  text-transform: uppercase;
  align-self: center;
  border-right: 1px solid #fff;
  padding: 5px 15px;
  font-size: 0.87em;
  line-height: 16px;
  letter-spacing: 1.25px;
  color: ${white};
  &:last-child {
    border-right: none;
  }
    `

export const Link = styled.div`
    align-content: center;
    text-align: left;
    cursor: pointer;
`